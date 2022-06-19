/*
    Copyright 2017-2022 Charles Korn.

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

package batect.docker.api

import batect.docker.ContainerCreationFailedException
import batect.docker.ContainerCreationRequest
import batect.docker.ContainerFilesystemItem
import batect.docker.DockerContainer
import batect.docker.DockerContainerInfo
import batect.docker.DockerEvent
import batect.docker.DockerException
import batect.docker.DockerHttpConfig
import batect.docker.Json
import batect.docker.data
import batect.docker.run.ConnectionHijacker
import batect.docker.run.ContainerInputStream
import batect.docker.run.ContainerOutputDecoder
import batect.docker.run.ContainerOutputStream
import batect.docker.toJsonArray
import batect.logging.LogMessageBuilder
import batect.logging.Logger
import batect.os.Dimensions
import batect.os.SystemInfo
import batect.primitives.CancellationContext
import batect.primitives.executeInCancellationContext
import jnr.constants.platform.Signal
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.HttpUrl
import okhttp3.Request
import okio.BufferedSource
import java.time.Duration
import java.util.concurrent.TimeUnit

class ContainersAPI(
    httpConfig: DockerHttpConfig,
    systemInfo: SystemInfo,
    logger: Logger,
    private val hijackerFactory: () -> ConnectionHijacker = ::ConnectionHijacker
) : APIBase(httpConfig, systemInfo, logger) {
    fun create(creationRequest: ContainerCreationRequest): DockerContainer {
        logger.info {
            message("Creating container.")
            data("request", creationRequest)
        }

        val url = urlForContainers.newBuilder()
            .addPathSegment("create")
            .addQueryParameter("name", creationRequest.name)
            .build()

        val body = creationRequest.toJson()

        val request = Request.Builder()
            .post(jsonRequestBody(body))
            .url(url)
            .build()

        clientWithTimeout(90, TimeUnit.SECONDS).newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Container creation failed.")
                    data("error", error)
                }

                throw ContainerCreationFailedException("Output from Docker was: ${error.message}")
            }

            val parsedResponse = Json.default.parseToJsonElement(response.body!!.string()).jsonObject
            val containerId = parsedResponse.getValue("Id").jsonPrimitive.content

            logger.info {
                message("Container created.")
                data("containerId", containerId)
                data("containerName", creationRequest.name)
            }

            return DockerContainer(containerId, creationRequest.name)
        }
    }

    fun start(container: DockerContainer) {
        logger.info {
            message("Starting container.")
            data("container", container)
        }

        val request = Request.Builder()
            .post(emptyRequestBody())
            .url(urlForContainerOperation(container, "start"))
            .build()

        clientWithTimeout(60, TimeUnit.SECONDS).newCall(request).execute().use { response ->
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

    fun inspect(container: DockerContainer): DockerContainerInfo {
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

            return Json.ignoringUnknownKeys.decodeFromString(DockerContainerInfo.serializer(), response.body!!.string())
        }
    }

    fun stop(container: DockerContainer) {
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
            if (response.code == 304) {
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

    fun remove(container: DockerContainer) {
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

        clientWithTimeout(60, TimeUnit.SECONDS).newCall(request).execute().use { response ->
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

    fun waitForNextEvent(container: DockerContainer, eventTypes: Iterable<String>, timeout: Duration, cancellationContext: CancellationContext): DockerEvent {
        logger.info {
            message("Getting next event for container.")
            data("container", container)
            data("eventTypes", eventTypes)
        }

        val filters = buildJsonObject {
            put("event", eventTypes.toJsonArray())
            put("container", listOf(container.id).toJsonArray())
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

        clientWithTimeout(timeout.toNanos(), TimeUnit.NANOSECONDS)
            .newCall(request)
            .executeInCancellationContext(cancellationContext) { response ->
                checkForFailure(response) { error ->
                    logger.error {
                        message("Getting events for container failed.")
                        data("error", error)
                    }

                    throw DockerException("Getting events for container '${container.id}' failed: ${error.message}")
                }

                val parsedEvent = response.body!!.source().readJsonObject()

                logger.info {
                    message("Received event for container.")
                    data("event", parsedEvent.toString())
                }

                return DockerEvent(parsedEvent.getValue("status").jsonPrimitive.content)
            }
    }

    fun waitForExit(container: DockerContainer, cancellationContext: CancellationContext): Long {
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

        clientWithNoTimeout()
            .newCall(request)
            .executeInCancellationContext(cancellationContext) { response ->
                checkForFailure(response) { error ->
                    logger.error {
                        message("Waiting for container to exit failed.")
                        data("error", error)
                    }

                    throw DockerException("Waiting for container '${container.id}' to exit failed: ${error.message}")
                }

                val responseBody = response.body!!.string()
                val parsedResponse = Json.default.parseToJsonElement(responseBody).jsonObject

                logger.info {
                    message("Container exited.")
                    data("result", responseBody)
                }

                if (parsedResponse.containsKey("Error") && parsedResponse.getValue("Error") !is JsonNull) {
                    val message = parsedResponse.getValue("Error").jsonObject.getValue("Message").jsonPrimitive.content

                    throw DockerException("Waiting for container '${container.id}' to exit succeeded but returned an error: $message")
                }

                return parsedResponse.getValue("StatusCode").jsonPrimitive.long
            }
    }

    // Note that these two methods assume that the container was created with the TTY option enabled, even if the local terminal is not a TTY.
    // The caller must call close() on the response to clean up all connections once it is finished with the streams.
    //
    // This entire thing is a bit of a gross hack. The WebSocket version of this API doesn't work properly on macOS (see https://github.com/docker/for-mac/issues/1662),
    // and OkHttp doesn't cleanly support Docker's non-standard connection hijacking mechanism.
    // And, to make things more complicated, we can't use the same socket for both container input and container output, as we need to be able to close
    // the input stream when there's no more input without closing the output stream - Java sockets don't seem to support closing one side of the
    // connection without also closing the other at the same time.
    fun attachToOutput(container: DockerContainer, isTTY: Boolean): ContainerOutputStream {
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
            .withNoReadTimeout()
            .connectionPoolWithNoEviction()
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

        return if (isTTY) {
            ContainerOutputStream(response, hijacker.source!!)
        } else {
            ContainerOutputStream(response, ContainerOutputDecoder(hijacker.source!!))
        }
    }

    fun attachToInput(container: DockerContainer): ContainerInputStream {
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
            .withNoReadTimeout()
            .connectionPoolWithNoEviction()
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

    fun sendSignal(container: DockerContainer, signal: Signal) {
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

    fun resizeTTY(container: DockerContainer, dimensions: Dimensions) {
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

                if (error.message.startsWith("cannot resize a stopped container") || error.message.endsWith("is not running")) {
                    throw ContainerStoppedException(message)
                }

                if (error.statusCode == 500 && (error.message.trim() == "bad file descriptor: unknown" || error.message.endsWith("the handle has already been closed"))) {
                    throw ContainerStoppedException("$message (the container may have stopped quickly after starting)")
                }

                throw DockerException(message)
            }
        }

        logger.info {
            message("Container TTY resized.")
        }
    }

    fun upload(container: DockerContainer, source: Set<ContainerFilesystemItem>, destination: String) {
        logger.info {
            message("Uploading items to container.")
            data("container", container)
            data("source", source, SetSerializer(ContainerFilesystemItem.serializer()))
            data("destination", destination)
        }

        val url = urlForContainer(container).newBuilder()
            .addPathSegment("archive")
            .addQueryParameter("path", destination)
            .build()

        val request = Request.Builder()
            .put(FilesystemUploadRequestBody(source))
            .url(url)
            .build()

        httpConfig.client.newCall(request).execute().use { response ->
            checkForFailure(response) { error ->
                logger.error {
                    message("Could not upload items to container.")
                    data("error", error)
                }

                throw DockerException("Uploading ${source.size} items to container '${container.id}' failed: ${error.message}")
            }
        }

        logger.info {
            message("Files uploaded.")
        }
    }

    private val urlForContainers: HttpUrl = baseUrl.newBuilder()
        .addPathSegment("containers")
        .build()

    private fun urlForContainer(container: DockerContainer): HttpUrl = urlForContainers.newBuilder()
        .addPathSegment(container.id)
        .build()

    private fun urlForContainerOperation(container: DockerContainer, operation: String): HttpUrl = urlForContainer(container).newBuilder()
        .addPathSegment(operation)
        .build()

    private fun LogMessageBuilder.data(key: String, value: ContainerCreationRequest) = this.data(key, value, ContainerCreationRequest.serializer())

    // HACK: This method is a workaround for two issues:
    // - starting with Docker 19.03.5, the /events API no longer sends new line characters between events, so we can't just read a full line of the response and parse that (see https://github.com/batect/batect/issues/393)
    // - kotlinx.serialization doesn't support reading JSON from a stream, it only supports reading from a string (see https://github.com/Kotlin/kotlinx.serialization/issues/204)
    //
    // Furthermore, we can't just read as much as we can from the stream in each go, because kotlinx.serialization doesn't allow you to just read as much as you can from the source string and stop once it's read a full object.
    private fun BufferedSource.readJsonObject(): JsonObject {
        val buffer = StringBuffer()

        while (true) {
            val possibleObjectEnd = this.indexOf('}'.code.toByte())

            if (possibleObjectEnd == -1L) {
                buffer.append(this.readUtf8())
            } else {
                buffer.append(this.readUtf8(possibleObjectEnd + 1))
            }

            try {
                return Json.default.parseToJsonElement(buffer.toString()).jsonObject
            } catch (e: Exception) {
                when (e) {
                    is SerializationException, is StringIndexOutOfBoundsException -> {} // Haven't read a full JSON object yet, keep reading.
                    else -> throw e
                }
            }
        }
    }
}
