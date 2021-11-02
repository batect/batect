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

package batect.cli.commands

import batect.config.ProjectPaths
import batect.docker.DockerVolume
import batect.docker.client.VolumesClient
import batect.execution.CacheManager
import batect.execution.CacheType
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.runForEachTest
import batect.ui.Console
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files
import kotlin.streams.toList

object CleanupCachesCommandSpec : Spek({
    describe("a cleanup caches command") {
        val volumesClient by createForEachTest {
            mock<VolumesClient> {
                on { getAll() } doReturn setOf(
                    DockerVolume("batect-cache-this-project-abc123"),
                    DockerVolume("batect-cache-this-project-def456"),
                    DockerVolume("batect-cache-other-project-abc123"),
                    DockerVolume("something-else")
                )
            }
        }

        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }

        val projectPaths by createForEachTest {
            mock<ProjectPaths> {
                on { cacheDirectory } doReturn fileSystem.getPath("/caches")
            }
        }

        val console by createForEachTest { mock<Console>() }

        beforeEachTest {
            Files.createDirectories(fileSystem.getPath("/caches", "empty-cache"))
            Files.createDirectories(fileSystem.getPath("/caches", "cache-with-file"))
            Files.createFile(fileSystem.getPath("/caches", "cache-with-file", "file"))
            Files.createDirectories(fileSystem.getPath("/caches", "cache-with-sub-directory"))
            Files.createDirectories(fileSystem.getPath("/caches", "cache-with-sub-directory", "sub-dir"))
            Files.createFile(fileSystem.getPath("/caches", "file-that-should-not-be-deleted"))
        }

        fun dockerConnectivity(cacheTypeToUse: CacheType): DockerConnectivity {
            val cacheManager = mock<CacheManager> {
                on { projectCacheKey } doReturn "this-project"
                on { cacheType } doReturn cacheTypeToUse
            }

            return fakeDockerConnectivity(
                DI.direct {
                    bind<CacheManager>() with instance(cacheManager)
                }
            )
        }

        given("volumes are being used for caches") {
            val cacheType = CacheType.Volume
            val command by createForEachTest { CleanupCachesCommand(dockerConnectivity(cacheType), volumesClient, projectPaths, console, emptySet()) }
            val exitCode by runForEachTest { command.run() }

            it("returns a zero exit code") {
                assertThat(exitCode, equalTo(0))
            }

            it("deletes both volumes used by the project for caches") {
                verify(volumesClient).delete(DockerVolume("batect-cache-this-project-abc123"))
                verify(volumesClient).delete(DockerVolume("batect-cache-this-project-def456"))
            }

            it("does not delete any other volumes") {
                verify(volumesClient, never()).delete(DockerVolume("batect-cache-other-project-abc123"))
                verify(volumesClient, never()).delete(DockerVolume("something-else"))
            }

            it("prints messages to the console at appropriate moments") {
                inOrder(console, volumesClient) {
                    verify(console).println("Checking for cache volumes...")
                    verify(volumesClient).getAll()
                    verify(console).println("Deleting volume 'batect-cache-this-project-abc123'...")
                    verify(volumesClient).delete(DockerVolume("batect-cache-this-project-abc123"))
                    verify(console).println("Deleting volume 'batect-cache-this-project-def456'...")
                    verify(volumesClient).delete(DockerVolume("batect-cache-this-project-def456"))
                    verify(console).println("Done! Deleted 2 volumes.")
                }
            }

            it("does not delete anything from the cache directory") {
                assertThat(
                    Files.list(projectPaths.cacheDirectory).toList().toSet(),
                    equalTo(
                        setOf(
                            fileSystem.getPath("/caches", "empty-cache"),
                            fileSystem.getPath("/caches", "cache-with-file"),
                            fileSystem.getPath("/caches", "cache-with-sub-directory"),
                            fileSystem.getPath("/caches", "file-that-should-not-be-deleted")
                        )
                    )
                )
            }
        }

        given("volumes name is provided for cache cleanup") {
            val cacheType = CacheType.Volume
            val cachesToClean = setOf("def456")
            val command by createForEachTest { CleanupCachesCommand(dockerConnectivity(cacheType), volumesClient, projectPaths, console, cachesToClean) }
            val exitCode by runForEachTest { command.run() }

            it("returns a zero exit code") {
                assertThat(exitCode, equalTo(0))
            }

            it("deletes the volumes used by the project for caches which matches input cache name") {
                verify(volumesClient).delete(DockerVolume("batect-cache-this-project-def456"))
            }

            it("does not delete any other volumes") {
                verify(volumesClient, never()).delete(DockerVolume("batect-cache-this-project-abc123"))
                verify(volumesClient, never()).delete(DockerVolume("batect-cache-other-project-abc123"))
                verify(volumesClient, never()).delete(DockerVolume("something-else"))
            }

            it("prints messages to the console at appropriate moments") {
                inOrder(console, volumesClient) {
                    verify(console).println("Checking for cache volumes...")
                    verify(volumesClient).getAll()
                    verify(console).println("Deleting volume 'batect-cache-this-project-def456'...")
                    verify(volumesClient).delete(DockerVolume("batect-cache-this-project-def456"))
                    verify(console).println("Done! Deleted 1 volume.")
                }
            }

            it("does not delete anything from the cache directory") {
                assertThat(
                    Files.list(projectPaths.cacheDirectory).toList().toSet(),
                    equalTo(
                        setOf(
                            fileSystem.getPath("/caches", "empty-cache"),
                            fileSystem.getPath("/caches", "cache-with-file"),
                            fileSystem.getPath("/caches", "cache-with-sub-directory"),
                            fileSystem.getPath("/caches", "file-that-should-not-be-deleted")
                        )
                    )
                )
            }
        }

        given("directories are being used for caches") {
            val cacheType = CacheType.Directory
            val command by createForEachTest { CleanupCachesCommand(dockerConnectivity(cacheType), volumesClient, projectPaths, console, emptySet()) }
            val exitCode by runForEachTest { command.run() }

            it("returns a zero exit code") {
                assertThat(exitCode, equalTo(0))
            }

            it("deletes all of the folders in the cache directory") {
                assertThat(Files.exists(fileSystem.getPath("/caches", "empty-cache")), equalTo(false))
                assertThat(Files.exists(fileSystem.getPath("/caches", "cache-with-file")), equalTo(false))
                assertThat(Files.exists(fileSystem.getPath("/caches", "cache-with-sub-directory")), equalTo(false))
            }

            it("does not delete any files in the cache directory") {
                assertThat(Files.exists(fileSystem.getPath("/caches", "file-that-should-not-be-deleted")), equalTo(true))
            }

            it("prints messages to the console at appropriate moments") {
                verify(console, times(1)).println("Checking for cache directories in '/caches'...")
                verify(console, times(1)).println("Done! Deleted 3 directories.")

                inOrder(console) {
                    verify(console).println("Checking for cache directories in '/caches'...")
                    verify(console).println("Deleting '/caches/empty-cache'...")
                    verify(console).println("Done! Deleted 3 directories.")
                }

                inOrder(console) {
                    verify(console).println("Checking for cache directories in '/caches'...")
                    verify(console).println("Deleting '/caches/cache-with-file'...")
                    verify(console).println("Done! Deleted 3 directories.")
                }

                inOrder(console) {
                    verify(console).println("Checking for cache directories in '/caches'...")
                    verify(console).println("Deleting '/caches/cache-with-sub-directory'...")
                    verify(console).println("Done! Deleted 3 directories.")
                }
            }

            it("does not delete any volumes") {
                verify(volumesClient, never()).delete(any())
            }
        }

        given("directories are being used for caches and directory relative path to cache is provided") {
            val cacheType = CacheType.Directory
            val cachesToClean = setOf("cache-with-file")
            val command by createForEachTest { CleanupCachesCommand(dockerConnectivity(cacheType), volumesClient, projectPaths, console, cachesToClean) }
            val exitCode by runForEachTest { command.run() }

            it("returns a zero exit code") {
                assertThat(exitCode, equalTo(0))
            }

            it("deletes all of the folders in the cache directory") {
                assertThat(Files.exists(fileSystem.getPath("/caches", "cache-with-file")), equalTo(false))
            }

            it("does not delete any other files or folder in the cache directory") {
                assertThat(Files.exists(fileSystem.getPath("/caches", "empty-cache")), equalTo(true))
                assertThat(Files.exists(fileSystem.getPath("/caches", "file-that-should-not-be-deleted")), equalTo(true))
                assertThat(Files.exists(fileSystem.getPath("/caches", "cache-with-sub-directory")), equalTo(true))
            }

            it("prints messages to the console at appropriate moments") {
                inOrder(console) {
                    verify(console).println("Checking for cache directories in '/caches'...")
                    verify(console).println("Deleting '/caches/cache-with-file'...")
                    verify(console).println("Done! Deleted 1 directory.")
                }
            }

            it("does not delete any volumes") {
                verify(volumesClient, never()).delete(any())
            }
        }
    }
})
