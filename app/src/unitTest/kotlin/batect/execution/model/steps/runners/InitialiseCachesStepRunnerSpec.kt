/*
   Copyright 2017-2021 Charles Korn.

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
import batect.config.LiteralValue
import batect.config.LocalMount
import batect.docker.ContainerCreationRequest
import batect.docker.DockerContainer
import batect.docker.DockerContainerRunResult
import batect.docker.DockerException
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.docker.DockerResourceNameGenerator
import batect.docker.DockerVolumeMount
import batect.docker.DockerVolumeMountSource
import batect.docker.HealthCheckConfig
import batect.docker.UserAndGroup
import batect.docker.client.ContainersClient
import batect.docker.client.DockerContainerType
import batect.docker.client.ImagesClient
import batect.docker.maximumHostNameLength
import batect.execution.ContainerDependencyGraph
import batect.execution.RunAsCurrentUserConfigurationProvider
import batect.execution.VolumeMountResolver
import batect.execution.model.events.CacheInitialisationFailedEvent
import batect.execution.model.events.CachesInitialisedEvent
import batect.execution.model.events.TaskEventSink
import batect.os.Dimensions
import batect.primitives.CancellationContext
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.pathResolutionContextDoesNotMatter
import okio.Sink
import okio.buffer
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe

object InitialiseCachesStepRunnerSpec : Spek({
    describe("running an 'initialise caches' step") {
        val cacheInitImageName = "batect-cache-init:abc123"
        val cacheInitImage = DockerImage("batect-cache-init")
        val imagesClient by createForEachTest {
            mock<ImagesClient> {
                on { pull(eq(cacheInitImageName), any(), any(), any()) } doReturn cacheInitImage
            }
        }

        val dockerContainer = DockerContainer("the-created-container")
        val containersClient by createForEachTest {
            mock<ContainersClient> {
                on { create(any()) } doReturn dockerContainer
                on { run(any(), any(), anyOrNull(), any(), any(), any(), any()) } doReturn DockerContainerRunResult(0)
            }
        }

        val cancellationContext by createForEachTest { mock<CancellationContext>() }
        val containerName = "batect-cache-init-abc123"
        val containerNameGenerator by createForEachTest {
            mock<DockerResourceNameGenerator> {
                on { generateNameFor("batect-cache-init", limitTo = maximumHostNameLength) } doReturn containerName
            }
        }

        val volumeMountResolver by createForEachTest { mock<VolumeMountResolver>() }
        val runAsCurrentUserConfigurationProvider by createForEachTest { mock<RunAsCurrentUserConfigurationProvider>() }

        val eventSink by createForEachTest { mock<TaskEventSink>() }

        fun Suite.itEmitsACachesInitialisedEvent() {
            it("emits a 'caches initialised' event") {
                verify(eventSink).postEvent(CachesInitialisedEvent)
            }
        }

        fun Suite.itEmitsACacheInitialisationFailedEvent(message: String) {
            it("emits a 'cache initialisation failed' event") {
                verify(eventSink).postEvent(CacheInitialisationFailedEvent(message))
            }

            it("does not emit a 'caches initialised' event") {
                verify(eventSink, never()).postEvent(CachesInitialisedEvent)
            }
        }

        fun Suite.itDoesNotRunTheCacheInitImage() {
            it("does not pull any images") {
                verify(imagesClient, never()).pull(any(), any(), any(), any())
            }

            it("does not run any containers") {
                verify(containersClient, never()).run(any(), any(), any(), any(), any(), any(), any())
            }
        }

        fun Suite.itPullsTheCacheInitImage() {
            it("pulls the cache init image") {
                verify(imagesClient).pull(eq(cacheInitImageName), eq(false), eq(cancellationContext), any())
            }
        }

        fun Suite.itCreatesTheCacheInitContainer(expectedInput: String, mounts: Set<DockerVolumeMount>) {
            it("creates the cache init container with the expected options") {
                verify(containersClient).create(
                    ContainerCreationRequest(
                        containerName,
                        cacheInitImage,
                        DockerNetwork("default"),
                        listOf(expectedInput),
                        emptyList(),
                        containerName,
                        emptySet(),
                        emptyMap(),
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
                        false,
                        Container.defaultLogDriver,
                        emptyMap(),
                        null
                    )
                )
            }
        }

        fun Suite.itRunsTheCacheInitContainer() {
            it("runs the cache init container") {
                verify(containersClient).run(eq(dockerContainer), any(), eq(null), eq(false), eq(cancellationContext), eq(Dimensions(0, 0)), any())
            }
        }

        fun Suite.itRemovesTheCacheInitContainer() {
            it("removes the cache init container") {
                verify(containersClient).remove(dockerContainer)
            }
        }

        fun runWithContainers(containerType: DockerContainerType, vararg containers: Container) {
            val graph = mock<ContainerDependencyGraph> {
                on { allContainers } doReturn setOf(*containers)
            }

            val runner = InitialiseCachesStepRunner(containerType, cacheInitImageName, imagesClient, containersClient, cancellationContext, containerNameGenerator, volumeMountResolver, runAsCurrentUserConfigurationProvider, graph)

            runner.run(eventSink)
        }

        given("Linux containers are being used") {
            val containerType = DockerContainerType.Linux

            given("no containers have caches") {
                val container1 = Container("container-1", imageSourceDoesNotMatter())
                val container2 = Container("container-2", imageSourceDoesNotMatter(), volumeMounts = setOf(LocalMount(LiteralValue("/some-path"), pathResolutionContextDoesNotMatter(), "/container-path")))

                beforeEachTest { runWithContainers(containerType, container1, container2) }

                itEmitsACachesInitialisedEvent()
                itDoesNotRunTheCacheInitImage()
            }

            given("a single container with a single cache") {
                val mount = CacheMount("some-cache", "/cache-mount-point")
                val container = Container("container-1", imageSourceDoesNotMatter(), volumeMounts = setOf(mount))

                given("volumes are being used for caches") {
                    beforeEachTest {
                        whenever(volumeMountResolver.resolve(mount)).doReturn(DockerVolumeMount(DockerVolumeMountSource.Volume("some-cache-abc123"), "/cache-mount-point"))
                    }

                    given("run as current user mode is disabled") {
                        beforeEachTest {
                            whenever(runAsCurrentUserConfigurationProvider.determineUserAndGroup(container)).thenReturn(null)
                        }

                        given("running the image succeeds") {
                            beforeEachTest { runWithContainers(containerType, container) }

                            val expectedInput = """{"caches":[{"path":"/caches/0"}]}"""

                            itEmitsACachesInitialisedEvent()
                            itPullsTheCacheInitImage()
                            itCreatesTheCacheInitContainer(expectedInput, setOf(DockerVolumeMount(DockerVolumeMountSource.Volume("some-cache-abc123"), "/caches/0")))
                            itRunsTheCacheInitContainer()
                            itRemovesTheCacheInitContainer()
                        }

                        given("running the container fails with a non-zero exit code") {
                            beforeEachTest {
                                whenever(containersClient.run(any(), any(), anyOrNull(), any(), any(), any(), any())).then { invocation ->
                                    val stdout = invocation.arguments[1] as Sink

                                    stdout.buffer().writeUtf8("Something went wrong.").flush()

                                    DockerContainerRunResult(123)
                                }
                            }

                            beforeEachTest { runWithContainers(containerType, container) }

                            itEmitsACacheInitialisationFailedEvent("Running the cache initialisation container failed: the container exited with exit code 123 and output:\nSomething went wrong.")
                            itRemovesTheCacheInitContainer()
                        }

                        given("pulling the image fails") {
                            beforeEachTest {
                                whenever(imagesClient.pull(any(), any(), any(), any())).thenThrow(DockerException("Something went wrong."))
                            }

                            beforeEachTest { runWithContainers(containerType, container) }

                            itEmitsACacheInitialisationFailedEvent("Pulling the cache initialisation image 'batect-cache-init:abc123' failed: Something went wrong.")
                        }

                        given("creating the container fails") {
                            beforeEachTest {
                                whenever(containersClient.create(any())).thenThrow(DockerException("Something went wrong."))
                            }

                            beforeEachTest { runWithContainers(containerType, container) }

                            itEmitsACacheInitialisationFailedEvent("Creating the cache initialisation container failed: Something went wrong.")
                        }

                        given("running the container fails") {
                            beforeEachTest {
                                whenever(containersClient.run(any(), any(), anyOrNull(), any(), any(), any(), any())).thenThrow(DockerException("Something went wrong."))
                            }

                            beforeEachTest { runWithContainers(containerType, container) }

                            itEmitsACacheInitialisationFailedEvent("Running the cache initialisation container failed: Something went wrong.")
                            itRemovesTheCacheInitContainer()
                        }

                        given("removing the container fails") {
                            beforeEachTest {
                                whenever(containersClient.remove(any())).thenThrow(DockerException("Something went wrong."))
                            }

                            beforeEachTest { runWithContainers(containerType, container) }

                            itEmitsACacheInitialisationFailedEvent("Removing the cache initialisation container failed: Something went wrong.")
                        }
                    }

                    given("run as current user mode is enabled") {
                        beforeEachTest {
                            whenever(runAsCurrentUserConfigurationProvider.determineUserAndGroup(container)).thenReturn(UserAndGroup(123, 456))
                        }

                        beforeEachTest { runWithContainers(containerType, container) }

                        val expectedInput = """{"caches":[{"path":"/caches/0","uid":123,"gid":456}]}"""

                        itEmitsACachesInitialisedEvent()
                        itPullsTheCacheInitImage()
                        itCreatesTheCacheInitContainer(expectedInput, setOf(DockerVolumeMount(DockerVolumeMountSource.Volume("some-cache-abc123"), "/caches/0")))
                        itRunsTheCacheInitContainer()
                        itRemovesTheCacheInitContainer()
                    }
                }

                given("local directories are being used for caches") {
                    beforeEachTest {
                        whenever(volumeMountResolver.resolve(mount)).doReturn(DockerVolumeMount(DockerVolumeMountSource.LocalPath("/batect/.caches/some-cache-abc123"), "/cache-mount-point"))
                    }

                    beforeEachTest { runWithContainers(containerType, container) }

                    itEmitsACachesInitialisedEvent()
                    itDoesNotRunTheCacheInitImage()
                }
            }

            given("a single container with multiple caches") {
                val mount1 = CacheMount("some-cache", "/cache-mount-point")
                val mount2 = CacheMount("some-other-cache", "/other-cache-mount-point")
                val container = Container("container-1", imageSourceDoesNotMatter(), volumeMounts = setOf(mount1, mount2))

                beforeEachTest {
                    whenever(volumeMountResolver.resolve(mount1)).doReturn(DockerVolumeMount(DockerVolumeMountSource.Volume("some-cache-abc123"), "/cache-mount-point"))
                    whenever(volumeMountResolver.resolve(mount2)).doReturn(DockerVolumeMount(DockerVolumeMountSource.Volume("some-other-cache-abc123"), "/other-cache-mount-point"))
                    whenever(runAsCurrentUserConfigurationProvider.determineUserAndGroup(container)).thenReturn(null)
                }

                beforeEachTest { runWithContainers(containerType, container) }

                val expectedInput = """{"caches":[{"path":"/caches/0"},{"path":"/caches/1"}]}"""

                itEmitsACachesInitialisedEvent()
                itPullsTheCacheInitImage()
                itCreatesTheCacheInitContainer(expectedInput, setOf(DockerVolumeMount(DockerVolumeMountSource.Volume("some-cache-abc123"), "/caches/0"), DockerVolumeMount(DockerVolumeMountSource.Volume("some-other-cache-abc123"), "/caches/1")))
                itRunsTheCacheInitContainer()
                itRemovesTheCacheInitContainer()
            }

            given("multiple containers each with their own caches") {
                val container1Mount = CacheMount("some-cache", "/cache-mount-point")
                val container1 = Container("container-1", imageSourceDoesNotMatter(), volumeMounts = setOf(container1Mount))
                val container2Mount = CacheMount("some-other-cache", "/other-cache-mount-point")
                val container2 = Container("container-2", imageSourceDoesNotMatter(), volumeMounts = setOf(container2Mount))

                beforeEachTest {
                    whenever(volumeMountResolver.resolve(container1Mount)).doReturn(DockerVolumeMount(DockerVolumeMountSource.Volume("some-cache-abc123"), "/cache-mount-point"))
                    whenever(volumeMountResolver.resolve(container2Mount)).doReturn(DockerVolumeMount(DockerVolumeMountSource.Volume("some-other-cache-abc123"), "/other-cache-mount-point"))

                    whenever(runAsCurrentUserConfigurationProvider.determineUserAndGroup(container1)).thenReturn(null)
                    whenever(runAsCurrentUserConfigurationProvider.determineUserAndGroup(container2)).thenReturn(UserAndGroup(123, 456))
                }

                beforeEachTest { runWithContainers(containerType, container1, container2) }

                val expectedInput = """{"caches":[{"path":"/caches/0"},{"path":"/caches/1","uid":123,"gid":456}]}"""

                itEmitsACachesInitialisedEvent()
                itPullsTheCacheInitImage()
                itCreatesTheCacheInitContainer(expectedInput, setOf(DockerVolumeMount(DockerVolumeMountSource.Volume("some-cache-abc123"), "/caches/0"), DockerVolumeMount(DockerVolumeMountSource.Volume("some-other-cache-abc123"), "/caches/1")))
                itRunsTheCacheInitContainer()
                itRemovesTheCacheInitContainer()
            }

            given("multiple containers sharing the same cache") {
                val container1Mount = CacheMount("some-cache", "/cache-mount-point")
                val container1 = Container("container-1", imageSourceDoesNotMatter(), volumeMounts = setOf(container1Mount))
                val container2Mount = CacheMount("some-cache", "/other-cache-mount-point")
                val container2 = Container("container-2", imageSourceDoesNotMatter(), volumeMounts = setOf(container2Mount))

                beforeEachTest {
                    whenever(volumeMountResolver.resolve(container1Mount)).doReturn(DockerVolumeMount(DockerVolumeMountSource.Volume("some-cache-abc123"), "/cache-mount-point"))
                    whenever(volumeMountResolver.resolve(container2Mount)).doReturn(DockerVolumeMount(DockerVolumeMountSource.Volume("some-cache-abc123"), "/other-cache-mount-point"))
                }

                given("neither container has run as current user enabled") {
                    beforeEachTest {
                        whenever(runAsCurrentUserConfigurationProvider.determineUserAndGroup(container1)).thenReturn(null)
                        whenever(runAsCurrentUserConfigurationProvider.determineUserAndGroup(container2)).thenReturn(null)
                    }

                    beforeEachTest { runWithContainers(containerType, container1, container2) }

                    val expectedInput = """{"caches":[{"path":"/caches/0"}]}"""

                    itEmitsACachesInitialisedEvent()
                    itPullsTheCacheInitImage()
                    itCreatesTheCacheInitContainer(expectedInput, setOf(DockerVolumeMount(DockerVolumeMountSource.Volume("some-cache-abc123"), "/caches/0")))
                    itRunsTheCacheInitContainer()
                    itRemovesTheCacheInitContainer()
                }

                given("both containers have run as current user enabled") {
                    beforeEachTest {
                        whenever(runAsCurrentUserConfigurationProvider.determineUserAndGroup(container1)).thenReturn(UserAndGroup(123, 456))
                        whenever(runAsCurrentUserConfigurationProvider.determineUserAndGroup(container2)).thenReturn(UserAndGroup(123, 456))
                    }

                    beforeEachTest { runWithContainers(containerType, container1, container2) }

                    val expectedInput = """{"caches":[{"path":"/caches/0","uid":123,"gid":456}]}"""

                    itEmitsACachesInitialisedEvent()
                    itPullsTheCacheInitImage()
                    itCreatesTheCacheInitContainer(expectedInput, setOf(DockerVolumeMount(DockerVolumeMountSource.Volume("some-cache-abc123"), "/caches/0")))
                    itRunsTheCacheInitContainer()
                    itRemovesTheCacheInitContainer()
                }

                given("one container has run as current user enabled and the other does not") {
                    beforeEachTest {
                        whenever(runAsCurrentUserConfigurationProvider.determineUserAndGroup(container1)).thenReturn(null)
                        whenever(runAsCurrentUserConfigurationProvider.determineUserAndGroup(container2)).thenReturn(UserAndGroup(123, 456))
                    }

                    beforeEachTest { runWithContainers(containerType, container1, container2) }

                    itEmitsACacheInitialisationFailedEvent("Containers 'container-1' and 'container-2' share the 'some-cache' cache, but one container has run as current user enabled and the other does not. Caches can only be shared by containers if they either both have run as current user enabled or both have it disabled.")
                    itDoesNotRunTheCacheInitImage()
                }
            }
        }

        given("Windows containers are being used") {
            val containerType = DockerContainerType.Windows
            val container1 = Container("container-1", imageSourceDoesNotMatter())
            val container2 = Container("container-2", imageSourceDoesNotMatter())

            beforeEachTest { runWithContainers(containerType, container1, container2) }

            itEmitsACachesInitialisedEvent()
            itDoesNotRunTheCacheInitImage()
        }
    }
})
