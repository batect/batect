/*
   Copyright 2017-2019 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.docker

import batect.docker.build.DockerImageBuildContext
import batect.docker.build.DockerImageBuildContextRequestBody
import batect.docker.pull.DockerRegistryCredentials
import batect.docker.run.ConnectionHijacker
import batect.docker.run.ContainerInputStream
import batect.docker.run.ContainerOutputStream
import batect.execution.CancellationContext
import batect.execution.executeInCancellationContext
import batect.logging.Logger
import batect.os.Dimensions
import batect.os.SystemInfo
import batect.utils.Json
import batect.utils.Version
import jnr.constants.platform.Signal
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.json
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

class DockerAPI(
    private val httpConfig: DockerHttpConfig,
    private val systemInfo: SystemInfo,
    private val logger: Logger,
    private val hijackerFactory: () -> ConnectionHijacker = ::ConnectionHijacker
) {
    fun createContainer(creationRequest: DockerContainerCreationRequest): DockerContainer {
        logger.info {
            message("Creating container.")
            data("request", creationRequest)
        }

        val url = urlForContainers().newBuilder()
            .addPathSegment("create")
            .build()

        val body = creationRequest.toJson()

        val request = Request.Builder()
            .post(jsonRequestBody(body))
            .url(url)
            .build()

        clientWithTimeout(30, TimeUnit.SECONDS).newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Container creation failed.")
                    data("error", error)
                }

                throw ContainerCreationFailedException("Output from Docker was: ${error.message}")
            }

            val parsedResponse = Json.parser.parseJson(response.body()!!.string()).jsonObject
            val containerId = parsedResponse.getValue("Id").primitive.content

            logger.info {
                message("Container created.")
                data("containerId", containerId)
            }

            return DockerContainer(containerId)
        }
    }

    fun startContainer(container: DockerContainer) {
        logger.info {
            message("Starting container.")
            data("container", container)
        }

        val request = Request.Builder()
            .post(emptyRequestBody())
            .url(urlForContainerOperation(container, "start"))
            .build()

        clientWithTimeout(30, TimeUnit.SECONDS).newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Starting container failed.")
                    data("error", error)
                }

                throw ContainerStartFailedException(container.id, error.message)
            }

            logger.info {
                message("Container started.")
                data("container", container)
            }
        }
    }

    fun inspectContainer(container: DockerContainer): DockerContainerInfo {
        logger.info {
            message("Inspecting container.")
            data("container", container)
        }

        val url = urlForContainer(container).newBuilder()
            .addPathSegment("json")
            .build()

        val request = Request.Builder()
            .get()
            .url(url)
            .build()

        httpConfig.client.newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not inspect container.")
                    data("container", container)
                    data("error", error)
                }

                throw ContainerInspectionFailedException("Could not inspect container '${container.id}': ${error.message}")
            }

            return Json.nonstrictParser.parse(DockerContainerInfo.serializer(), response.body()!!.string())
        }
    }

    fun stopContainer(container: DockerContainer) {
        logger.info {
            message("Stopping container.")
            data("container", container)
        }

        val timeoutInSeconds = 10L

        val url = urlForContainerOperation(container, "stop").newBuilder()
            .addQueryParameter("timeout", timeoutInSeconds.toString())
            .build()

        val request = Request.Builder()
            .post(emptyRequestBody())
            .url(url)
            .build()

        clientWithTimeout(timeoutInSeconds + 10, TimeUnit.SECONDS).newCall(request).execute().use { response ->
            if (response.code() == 304) {
                logger.warn {
                    message("Container has already stopped.")
                }

                return
            }

            checkForFailure(response) { error ->
                logger.error {
                    message("Could not stop container.")
                    data("error", error)
                }

                throw ContainerStopFailedException(container.id, error.message)
            }
        }

        logger.info {
            message("Container stopped.")
        }
    }

    fun removeContainer(container: DockerContainer) {
        logger.info {
            message("Removing container.")
            data("container", container)
        }

        val url = urlForContainer(container).newBuilder()
            .addQueryParameter("v", "true")
            .addQueryParameter("force", "true")
            .build()

        val request = Request.Builder()
            .delete()
            .url(url)
            .build()

        clientWithTimeout(20, TimeUnit.SECONDS).newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not remove container.")
                    data("error", error)
                }

                throw ContainerRemovalFailedException(container.id, error.message)
            }
        }

        logger.info {
            message("Container removed.")
        }
    }

    fun waitForNextEventForContainer(container: DockerContainer, eventTypes: Iterable<String>, timeout: Duration): DockerEvent {
        logger.info {
            message("Getting next event for container.")
            data("container", container)
            data("eventTypes", eventTypes)
        }

        val filters = json {
            "event" to eventTypes.toJsonArray()
            "container" to listOf(container.id).toJsonArray()
        }

        val url = baseUrl.newBuilder()
            .addPathSegment("events")
            .addQueryParameter("since", "0")
            .addQueryParameter("filters", filters.toString())
            .build()

        val request = Request.Builder()
            .get()
            .url(url)
            .build()

        clientWithTimeout(timeout.toNanos(), TimeUnit.NANOSECONDS).newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Getting events for container failed.")
                    data("error", error)
                }

                throw DockerException("Getting events for container '${container.id}' failed: ${error.message}")
            }

            val firstEvent = response.body()!!.source().readUtf8LineStrict()
            val parsedEvent = Json.parser.parseJson(firstEvent).jsonObject

            logger.info {
                message("Received event for container.")
                data("event", firstEvent)
            }

            return DockerEvent(parsedEvent.getValue("status").primitive.content)
        }
    }

    fun waitForExit(container: DockerContainer): Int {
        logger.info {
            message("Waiting for container to exit.")
            data("container", container)
        }

        val url = urlForContainer(container).newBuilder()
            .addPathSegment("wait")
            .addQueryParameter("condition", "next-exit")
            .build()

        val request = Request.Builder()
            .post(emptyRequestBody())
            .url(url)
            .build()

        clientWithNoTimeout().newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Waiting for container to exit failed.")
                    data("error", error)
                }

                throw DockerException("Waiting for container '${container.id}' to exit failed: ${error.message}")
            }

            val responseBody = response.body()!!.string()
            val parsedResponse = Json.parser.parseJson(responseBody).jsonObject

            logger.info {
                message("Container exited.")
                data("result", responseBody)
            }

            if (parsedResponse.containsKey("Error") && !parsedResponse.getValue("Error").isNull) {
                val message = parsedResponse.getObject("Error").getPrimitive("Message").content

                throw DockerException("Waiting for container '${container.id}' to exit succeeded but returned an error: $message")
            }

            return parsedResponse.getValue("StatusCode").primitive.int
        }
    }

    // Note that these two methods assume that the container was created with the TTY option enabled, even if the local terminal is not a TTY.
    // The caller must call close() on the response to clean up all connections once it is finished with the streams.
    //
    // This entire thing is a bit of a gross hack. The WebSocket version of this API doesn't work properly on OS X (see https://github.com/docker/for-mac/issues/1662),
    // and OkHttp doesn't cleanly support Docker's non-standard connection hijacking mechanism.
    // And, to make things more complicated, we can't use the same socket for both container input and container output, as we need to be able to close
    // the input stream when there's no more input without closing the output stream - Java sockets don't seem to support closing one side of the
    // connection without also closing the other at the same time.
    fun attachToContainerOutput(container: DockerContainer): ContainerOutputStream {
        logger.info {
            message("Attaching to container output.")
            data("container", container)
        }

        val url = urlForContainer(container).newBuilder()
            .addPathSegment("attach")
            .addQueryParameter("logs", "true")
            .addQueryParameter("stream", "true")
            .addQueryParameter("stdout", "true")
            .addQueryParameter("stderr", "true")
            .build()

        val request = Request.Builder()
            .post(emptyRequestBody())
            .url(url)
            .header("Connection", "Upgrade")
            .header("Upgrade", "tcp")
            .build()

        val hijacker = hijackerFactory()

        val client = httpConfig.client.newBuilder()
            .readTimeout(0, TimeUnit.NANOSECONDS)
            .connectionPool(ConnectionPool(5, Long.MAX_VALUE, TimeUnit.NANOSECONDS))
            .addNetworkInterceptor(hijacker)
            .build()

        val response = client.newCall(request).execute()

        checkForFailure(response) { error ->
            logger.error {
                message("Attaching to container output failed.")
                data("error", error)
            }

            throw DockerException("Attaching to output from container '${container.id}' failed: ${error.message}")
        }

        return ContainerOutputStream(response, hijacker.source!!)
    }

    fun attachToContainerInput(container: DockerContainer): ContainerInputStream {
        logger.info {
            message("Attaching to container input.")
            data("container", container)
        }

        val url = urlForContainer(container).newBuilder()
            .addPathSegment("attach")
            .addQueryParameter("logs", "true")
            .addQueryParameter("stream", "true")
            .addQueryParameter("stdin", "true")
            .build()

        val request = Request.Builder()
            .post(emptyRequestBody())
            .url(url)
            .header("Connection", "Upgrade")
            .header("Upgrade", "tcp")
            .build()

        val hijacker = hijackerFactory()

        val client = httpConfig.client.newBuilder()
            .readTimeout(0, TimeUnit.NANOSECONDS)
            .connectionPool(ConnectionPool(5, Long.MAX_VALUE, TimeUnit.NANOSECONDS))
            .addNetworkInterceptor(hijacker)
            .build()

        val response = client.newCall(request).execute()

        checkForFailure(response) { error ->
            logger.error {
                message("Attaching to container input failed.")
                data("error", error)
            }

            throw DockerException("Attaching to input for container '${container.id}' failed: ${error.message}")
        }

        return ContainerInputStream(response, hijacker.sink!!)
    }

    fun sendSignalToContainer(container: DockerContainer, signal: Signal) {
        logger.info {
            message("Sending signal to container.")
            data("container", container)
            data("signal", signal.name)
        }

        val url = urlForContainer(container).newBuilder()
            .addPathSegment("kill")
            .addQueryParameter("signal", signal.name)
            .build()

        val request = Request.Builder()
            .post(emptyRequestBody())
            .url(url)
            .build()

        httpConfig.client.newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not send signal to container.")
                    data("error", error)
                }

                throw DockerException("Sending signal ${signal.name} to container '${container.id}' failed: ${error.message}")
            }
        }

        logger.info {
            message("Signal sent to container.")
        }
    }

    fun resizeContainerTTY(container: DockerContainer, dimensions: Dimensions) {
        logger.info {
            message("Resizing container TTY.")
            data("container", container)
            data("height", dimensions.height)
            data("width", dimensions.width)
        }

        val url = urlForContainer(container).newBuilder()
            .addPathSegment("resize")
            .addQueryParameter("h", dimensions.height.toString())
            .addQueryParameter("w", dimensions.width.toString())
            .build()

        val request = Request.Builder()
            .post(emptyRequestBody())
            .url(url)
            .build()

        httpConfig.client.newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not resize container TTY.")
                    data("error", error)
                }

                val message = "Resizing TTY for container '${container.id}' failed: ${error.message}"

                if (error.message.startsWith("cannot resize a stopped container")) {
                    throw ContainerStoppedException(message)
                }

                if (error.statusCode == 500 && error.message.trim() == "bad file descriptor: unknown") {
                    throw ContainerStoppedException("$message (the container may have stopped quickly after starting)")
                }

                throw DockerException(message)
            }
        }

        logger.info {
            message("Container TTY resized.")
        }
    }

    fun createNetwork(): DockerNetwork {
        logger.info {
            message("Creating new network.")
        }

        val url = urlForNetworks().newBuilder()
            .addPathSegment("create")
            .build()

        val body = json {
            "Name" to UUID.randomUUID().toString()
            "CheckDuplicate" to true
            "Driver" to "bridge"
        }

        val request = Request.Builder()
            .post(jsonRequestBody(body))
            .url(url)
            .build()

        httpConfig.client.newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not create network.")
                    data("error", error)
                }

                throw NetworkCreationFailedException(error.message)
            }

            val parsedResponse = Json.parser.parseJson(response.body()!!.string()).jsonObject
            val networkId = parsedResponse.getValue("Id").primitive.content

            logger.info {
                message("Network created.")
                data("networkId", networkId)
            }

            return DockerNetwork(networkId)
        }
    }

    fun deleteNetwork(network: DockerNetwork) {
        logger.info {
            message("Deleting network.")
            data("network", network)
        }

        val request = Request.Builder()
            .delete()
            .url(urlForNetwork(network))
            .build()

        httpConfig.client.newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not delete network.")
                    data("error", error)
                }

                throw NetworkDeletionFailedException(network.id, error.message)
            }
        }

        logger.info {
            message("Network deleted.")
        }
    }

    fun buildImage(
        context: DockerImageBuildContext,
        buildArgs: Map<String, String>,
        dockerfilePath: String,
        imageTags: Set<String>,
        registryCredentials: DockerRegistryCredentials?,
        onProgressUpdate: (JsonObject) -> Unit
    ): DockerImage {
        logger.info {
            message("Building image.")
            data("context", context)
            data("buildArgs", buildArgs)
            data("imageTags", imageTags)
        }

        val request = createImageBuildRequest(context, buildArgs, dockerfilePath, imageTags, registryCredentials)

        clientWithNoTimeout().newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not build image.")
                    data("error", error)
                }

                throw ImageBuildFailedException("Building image failed: ${error.message}")
            }

            var builtImageId: String? = null
            val outputSoFar = StringBuilder()

            response.body()!!.charStream().forEachLine { line ->
                val parsedLine = Json.parser.parseJson(line).jsonObject
                val output = parsedLine.getPrimitiveOrNull("stream")?.content
                val error = parsedLine.getPrimitiveOrNull("error")?.content

                if (output != null) {
                    outputSoFar.append(output.correctLineEndings())
                }

                if (error != null) {
                    outputSoFar.append(error.correctLineEndings())
                    throw ImageBuildFailedException("Building image failed: $error. Output from build process was:" + systemInfo.lineSeparator + outputSoFar.trim().toString())
                }

                val imageId = parsedLine.getObjectOrNull("aux")?.getPrimitiveOrNull("ID")?.content

                if (imageId != null) {
                    builtImageId = imageId
                }

                onProgressUpdate(parsedLine)
            }

            if (builtImageId == null) {
                throw ImageBuildFailedException("Building image failed: daemon never sent built image ID.")
            }

            logger.info {
                message("Image built.")
            }

            return DockerImage(builtImageId!!)
        }
    }

    private fun createImageBuildRequest(
        context: DockerImageBuildContext,
        buildArgs: Map<String, String>,
        dockerfilePath: String,
        imageTags: Set<String>,
        registryCredentials: DockerRegistryCredentials?
    ): Request {
        val url = baseUrl.newBuilder()
            .addPathSegment("build")
            .addQueryParameter("buildargs", buildArgs.toJsonObject().toString())
            .addQueryParameter("dockerfile", dockerfilePath)

        imageTags.forEach { url.addQueryParameter("t", it) }

        return Request.Builder()
            .post(DockerImageBuildContextRequestBody(context))
            .url(url.build())
            .addRegistryCredentialsForBuild(registryCredentials)
            .build()
    }

    private fun Request.Builder.addRegistryCredentialsForBuild(registryCredentials: DockerRegistryCredentials?): Request.Builder {
        if (registryCredentials != null) {
            val jsonCredentials = json {
                registryCredentials.serverAddress to registryCredentials.toJSON()
            }

            val credentialBytes = jsonCredentials.toString().toByteArray()
            val encodedCredentials = Base64.getEncoder().encodeToString(credentialBytes)

            this.header("X-Registry-Config", encodedCredentials)
        }

        return this
    }

    fun pullImage(
        imageName: String,
        registryCredentials: DockerRegistryCredentials?,
        cancellationContext: CancellationContext,
        onProgressUpdate: (JsonObject) -> Unit
    ) {
        logger.info {
            message("Pulling image.")
            data("imageName", imageName)
        }

        val url = urlForImages().newBuilder()
            .addPathSegment("create")
            .addQueryParameter("fromImage", imageName)
            .build()

        val request = Request.Builder()
            .post(emptyRequestBody())
            .url(url)
            .addRegistryCredentialsForPull(registryCredentials)
            .build()

        clientWithTimeout(20, TimeUnit.SECONDS)
            .newCall(request)
            .executeInCancellationContext(cancellationContext) { response ->
                checkForFailure(response) { error ->
                    logger.error {
                        message("Could not pull image.")
                        data("error", error)
                    }

                    throw ImagePullFailedException("Pulling image '$imageName' failed: ${error.message}")
                }

                response.body()!!.charStream().forEachLine { line ->
                    val parsedLine = Json.parser.parseJson(line).jsonObject

                    if (parsedLine.containsKey("error")) {
                        val message = parsedLine.getValue("error").primitive.content
                            .correctLineEndings()

                        throw ImagePullFailedException("Pulling image '$imageName' failed: $message")
                    }

                    onProgressUpdate(parsedLine)
                }
            }

        logger.info {
            message("Image pulled.")
        }
    }

    private fun Request.Builder.addRegistryCredentialsForPull(registryCredentials: DockerRegistryCredentials?): Request.Builder {
        if (registryCredentials != null) {
            val credentialBytes = registryCredentials.toJSON().toString().toByteArray()
            val encodedCredentials = Base64.getEncoder().encodeToString(credentialBytes)

            this.header("X-Registry-Auth", encodedCredentials)
        }

        return this
    }

    fun hasImage(imageName: String): Boolean {
        logger.info {
            message("Checking if image has already been pulled.")
            data("imageName", imageName)
        }

        val filters = json {
            "reference" to listOf(imageName).toJsonArray()
        }

        val url = urlForImages().newBuilder()
            .addPathSegment("json")
            .addQueryParameter("filters", filters.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .build()

        httpConfig.client.newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not check if image has already been pulled.")
                    data("error", error)
                }

                throw ImagePullFailedException("Checking if image '$imageName' has already been pulled failed: ${error.message}")
            }

            val body = response.body()!!.string()
            val parsedBody = Json.parser.parseJson(body) as JsonArray

            return parsedBody.isNotEmpty()
        }
    }

    fun getServerVersionInfo(): DockerVersionInfo {
        logger.info {
            message("Getting Docker version information.")
        }

        val url = baseUrl.newBuilder()
            .addPathSegment("version")
            .build()

        val request = Request.Builder()
            .get()
            .url(url)
            .build()

        httpConfig.client.newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not get Docker version info.")
                    data("error", error)
                }

                throw DockerVersionInfoRetrievalException("The request failed: ${error.message}")
            }

            val parsedResponse = Json.parser.parseJson(response.body()!!.string()).jsonObject
            val version = parsedResponse.getValue("Version").primitive.content
            val apiVersion = parsedResponse.getValue("ApiVersion").primitive.content
            val minAPIVersion = parsedResponse.getValue("MinAPIVersion").primitive.content
            val gitCommit = parsedResponse.getValue("GitCommit").primitive.content

            return DockerVersionInfo(
                Version.parse(version),
                apiVersion,
                minAPIVersion,
                gitCommit
            )
        }
    }

    fun ping() {
        logger.info {
            message("Pinging daemon.")
        }

        val url = httpConfig.baseUrl.newBuilder()
            .addPathSegment("_ping")
            .build()

        val request = Request.Builder()
            .get()
            .url(url)
            .build()

        httpConfig.client.newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not ping daemon.")
                    data("error", error)
                }

                throw DockerException("Could not ping Docker daemon, daemon responded with HTTP ${response.code()}: ${error.message}")
            }

            val responseBody = response.body()!!.string()

            if (responseBody != "OK") {
                throw DockerException("Could not ping Docker daemon, daemon responded with HTTP ${response.code()}: $responseBody")
            }
        }
    }

    private fun clientWithTimeout(quantity: Long, unit: TimeUnit): OkHttpClient = httpConfig.client.newBuilder()
        .readTimeout(quantity, unit)
        .build()

    private fun clientWithNoTimeout() = clientWithTimeout(0, TimeUnit.NANOSECONDS)

    private val baseUrl: HttpUrl = httpConfig.baseUrl.newBuilder()
        .addPathSegment("v$minimumDockerAPIVersion")
        .build()

    private fun urlForContainers(): HttpUrl = baseUrl.newBuilder()
        .addPathSegment("containers")
        .build()

    private fun urlForContainer(container: DockerContainer): HttpUrl = urlForContainers().newBuilder()
        .addPathSegment(container.id)
        .build()

    private fun urlForContainerOperation(container: DockerContainer, operation: String): HttpUrl = urlForContainer(container).newBuilder()
        .addPathSegment(operation)
        .build()

    private fun urlForNetworks(): HttpUrl = baseUrl.newBuilder()
        .addPathSegment("networks")
        .build()

    private fun urlForNetwork(network: DockerNetwork): HttpUrl = urlForNetworks().newBuilder()
        .addPathSegment(network.id)
        .build()

    private fun urlForImages(): HttpUrl = baseUrl.newBuilder()
        .addPathSegment("images")
        .build()

    private fun emptyRequestBody(): RequestBody = RequestBody.create(MediaType.get("text/plain"), "")

    private val jsonMediaType = MediaType.get("application/json")
    private fun jsonRequestBody(json: JsonObject): RequestBody = jsonRequestBody(json.toString())
    private fun jsonRequestBody(json: String): RequestBody = RequestBody.create(jsonMediaType, json)

    private inline fun checkForFailure(response: Response, errorHandler: (DockerAPIError) -> Unit) {
        if (response.isSuccessful || response.code() == 101) {
            return
        }

        val responseBody = response.body()!!.string().trim()
        val contentType = response.body()!!.contentType()!!

        if (contentType.type() != jsonMediaType.type() || contentType.subtype() != jsonMediaType.subtype()) {
            logger.warn {
                message("Error response from Docker daemon was not in JSON format.")
                data("statusCode", response.code())
                data("message", responseBody)
            }

            errorHandler(DockerAPIError(response.code(), responseBody))
            return
        }

        val parsedError = Json.parser.parseJson(responseBody).jsonObject
        val message = parsedError.getValue("message").primitive.content
            .correctLineEndings()

        errorHandler(DockerAPIError(response.code(), message))
    }

    private data class DockerAPIError(val statusCode: Int, val message: String)

    private fun String.correctLineEndings(): String = this.replace("\n", systemInfo.lineSeparator)
}
