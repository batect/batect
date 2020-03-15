/*
   Copyright 2017-2020 Charles Korn.

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

package batect.execution.model.steps.runners

import batect.config.CacheMount
import batect.config.Container
import batect.config.HealthCheckConfig
import batect.docker.DockerContainer
import batect.docker.DockerContainerCreationRequest
import batect.docker.DockerContainerNameGenerator
import batect.docker.DockerException
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.docker.DockerVolumeMount
import batect.docker.DockerVolumeMountSource
import batect.docker.UserAndGroup
import batect.docker.client.DockerContainerType
import batect.docker.client.DockerContainersClient
import batect.docker.client.DockerImagesClient
import batect.execution.CancellationContext
import batect.execution.RunAsCurrentUserConfigurationProvider
import batect.execution.VolumeMountResolver
import batect.execution.model.events.CacheInitialisationFailedEvent
import batect.execution.model.events.CachesInitialisedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.InitialiseCachesStep
import batect.os.Dimensions
import batect.utils.Json
import kotlinx.io.ByteArrayOutputStream
import kotlinx.serialization.Serializable
import okio.sink

class InitialiseCachesStepRunner(
    private val cacheInitImageName: String,
    private val imagesClient: DockerImagesClient,
    private val containersClient: DockerContainersClient,
    private val cancellationContext: CancellationContext,
    private val containerNameGenerator: DockerContainerNameGenerator,
    private val volumeMountResolver: VolumeMountResolver,
    private val runAsCurrentUserConfigurationProvider: RunAsCurrentUserConfigurationProvider
) {
    fun run(step: InitialiseCachesStep, eventSink: TaskEventSink) {
        if (step.containerType == DockerContainerType.Windows) {
            eventSink.postEvent(CachesInitialisedEvent)
            return
        }

        try {
            val caches = determineCachesToInitialise(step.allContainersInTask)

            if (caches.isEmpty()) {
                eventSink.postEvent(CachesInitialisedEvent)
                return
            }

            val creationRequest = containerCreationRequest(caches)
            val container = create(creationRequest)

            try {
                run(container)
            } finally {
                remove(container)
            }

            eventSink.postEvent(CachesInitialisedEvent)
        } catch (e: InvalidCacheConfigurationException) {
            eventSink.postEvent(CacheInitialisationFailedEvent(e.message ?: ""))
        } catch (e: CacheInitialisationException) {
            eventSink.postEvent(CacheInitialisationFailedEvent(e.message ?: ""))
        }
    }

    private fun determineCachesToInitialise(containers: Set<Container>): Map<DockerVolumeMountSource.Volume, UserAndGroup?> {
        val caches = mutableMapOf<DockerVolumeMountSource.Volume, UserAndGroup?>()
        val cacheConfigSource = mutableMapOf<DockerVolumeMountSource.Volume, Container>()

        containers.forEach { container ->
            val containerCacheMounts = container.volumeMounts.filterIsInstance<CacheMount>()
            val userAndGroup = runAsCurrentUserConfigurationProvider.determineUserAndGroup(container)

            containerCacheMounts.forEach { cache ->
                val volumeMount = volumeMountResolver.resolve(cache)

                if (volumeMount.source is DockerVolumeMountSource.Volume) {
                    val volume = volumeMount.source

                    if (!caches.containsKey(volume)) {
                        caches[volume] = userAndGroup
                        cacheConfigSource[volume] = container
                    } else if (caches[volume] != userAndGroup) {
                        val otherContainer = cacheConfigSource.getValue(volume)

                        throw InvalidCacheConfigurationException("Containers '${otherContainer.name}' and '${container.name}' share the '${cache.name}' cache, but one has run as current user enabled and the other does not. Caches can only be shared by containers if they either both have run as current user enabled or both have it disabled.")
                    }
                }
            }
        }

        return caches
    }

    private fun containerCreationRequest(volumes: Map<DockerVolumeMountSource.Volume, UserAndGroup?>): DockerContainerCreationRequest {
        val image = pullImage()
        val containerName = containerNameGenerator.generateNameFor("batect-cache-init")
        val mounts = mutableSetOf<DockerVolumeMount>()
        val configs = mutableSetOf<CacheConfig>()

        volumes.keys.forEachIndexed { i, source ->
            val path = "/caches/$i"
            val userAndGroup = volumes[source]

            mounts.add(DockerVolumeMount(source, path))
            configs.add(CacheConfig(path, userAndGroup?.userId, userAndGroup?.groupId))
        }

        val input = Json.withoutDefaults.stringify(CacheInitConfig.serializer(), CacheInitConfig(configs))

        return DockerContainerCreationRequest(
            containerName,
            image,
            DockerNetwork("default"),
            listOf(input),
            emptyList(),
            containerName,
            emptySet(),
            emptyMap(),
            null,
            mounts,
            emptySet(),
            emptySet(),
            HealthCheckConfig(),
            null,
            false,
            false,
            emptySet(),
            emptySet(),
            false,
            false
        )
    }

    private fun pullImage(): DockerImage = try {
        imagesClient.pull(cacheInitImageName, cancellationContext, {})
    } catch (e: DockerException) {
        throw CacheInitialisationException("Pulling the cache initialisation image '$cacheInitImageName' failed: ${e.message}", e)
    }

    private fun create(creationRequest: DockerContainerCreationRequest): DockerContainer = try {
        containersClient.create(creationRequest)
    } catch (e: DockerException) {
        throw CacheInitialisationException("Creating the cache initialisation container failed: ${e.message}", e)
    }

    private fun run(container: DockerContainer) {
        try {
            val output = ByteArrayOutputStream()
            val result = containersClient.run(container, output.sink(), null, false, cancellationContext, Dimensions(0, 0), {})

            if (result.exitCode != 0L) {
                throw CacheInitialisationException("Running the cache initialisation container failed: the container exited with exit code ${result.exitCode} and output:\n$output")
            }
        } catch (e: DockerException) {
            throw CacheInitialisationException("Running the cache initialisation container failed: ${e.message}", e)
        }
    }

    private fun remove(container: DockerContainer) = try {
        containersClient.remove(container)
    } catch (e: DockerException) {
        throw CacheInitialisationException("Removing the cache initialisation container failed: ${e.message}", e)
    }

    @Serializable
    private data class CacheInitConfig(val caches: Set<CacheConfig>)

    @Serializable
    private data class CacheConfig(val path: String, val uid: Int? = null, val gid: Int? = null)

    private class InvalidCacheConfigurationException(message: String) : RuntimeException(message)
    private class CacheInitialisationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
}
