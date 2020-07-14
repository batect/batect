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

package batect.execution

import batect.config.CacheMount
import batect.config.EnvironmentVariableReference
import batect.config.ExpressionEvaluationContext
import batect.config.LiteralValue
import batect.config.LocalMount
import batect.config.ProjectPaths
import batect.docker.DockerVolumeMount
import batect.docker.DockerVolumeMountSource
import batect.os.HostEnvironmentVariables
import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.os.PathResolverFactory
import batect.os.PathType
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.withMessage
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import java.nio.file.Files
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object VolumeMountResolverSpec : Spek({
    describe("a volume mount resolver") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }

        describe("resolving local mounts") {
            val relativeTo by createForEachTest { fileSystem.getPath("/relative-to-path") }

            val pathResolver by createForEachTest {
                mock<PathResolver> {
                    on { resolve("file") } doReturn PathResolutionResult.Resolved("file", fileSystem.getPath("/resolved/file"), PathType.File)
                    on { resolve("directory") } doReturn PathResolutionResult.Resolved("directory", fileSystem.getPath("/resolved/directory"), PathType.Directory)
                    on { resolve("other") } doReturn PathResolutionResult.Resolved("other", fileSystem.getPath("/resolved/other"), PathType.Other)
                    on { resolve("does-not-exist") } doReturn PathResolutionResult.Resolved("does-not-exist", fileSystem.getPath("/resolved/does-not-exist"), PathType.DoesNotExist)
                    on { resolve("invalid") } doReturn PathResolutionResult.InvalidPath("invalid")
                }
            }

            val pathResolverFactory by createForEachTest {
                mock<PathResolverFactory> {
                    on { createResolver(relativeTo) } doReturn pathResolver
                }
            }

            val expressionEvaluationContext = ExpressionEvaluationContext(HostEnvironmentVariables("INVALID" to "invalid"), emptyMap())

            val resolver by createForEachTest { VolumeMountResolver(pathResolverFactory, expressionEvaluationContext, mock(), mock()) }

            given("a set of volume mounts from the configuration file that resolve to valid paths") {
                val mounts by createForEachTest {
                    setOf(
                        LocalMount(LiteralValue("file"), relativeTo, "/container-1"),
                        LocalMount(LiteralValue("directory"), relativeTo, "/container-2", "options-2"),
                        LocalMount(LiteralValue("other"), relativeTo, "/container-3"),
                        LocalMount(LiteralValue("does-not-exist"), relativeTo, "/container-4")
                    )
                }

                it("resolves the local mount paths, preserving the container path and options") {
                    assertThat(
                        resolver.resolve(mounts), equalTo(
                            setOf(
                                DockerVolumeMount(DockerVolumeMountSource.LocalPath("/resolved/file"), "/container-1"),
                                DockerVolumeMount(DockerVolumeMountSource.LocalPath("/resolved/directory"), "/container-2", "options-2"),
                                DockerVolumeMount(DockerVolumeMountSource.LocalPath("/resolved/other"), "/container-3"),
                                DockerVolumeMount(DockerVolumeMountSource.LocalPath("/resolved/does-not-exist"), "/container-4")
                            )
                        )
                    )
                }
            }

            given("a volume mount for the Docker socket") {
                val mounts by createForEachTest {
                    setOf(
                        LocalMount(LiteralValue("/var/run/docker.sock"), relativeTo, "/container-1", "some-options")
                    )
                }

                it("does not attempt to resolve the path and mounts it into the container") {
                    assertThat(
                        resolver.resolve(mounts), equalTo(
                            setOf(
                                DockerVolumeMount(DockerVolumeMountSource.LocalPath("/var/run/docker.sock"), "/container-1", "some-options")
                            )
                        )
                    )
                }
            }

            given("a volume mount with an invalid path") {
                given("the path does not contain an expression") {
                    val mounts by createForEachTest {
                        setOf(
                            LocalMount(LiteralValue("invalid"), relativeTo, "/container-1")
                        )
                    }

                    it("throws an appropriate exception") {
                        assertThat({ resolver.resolve(mounts) }, throws<VolumeMountResolutionException>(withMessage("Could not resolve volume mount path: 'invalid' is not a valid path.")))
                    }
                }

                given("the path contains an expression") {
                    val mounts by createForEachTest {
                        setOf(
                            LocalMount(EnvironmentVariableReference("INVALID", originalExpression = "the-original-invalid-expression"), relativeTo, "/container-1")
                        )
                    }

                    it("throws an appropriate exception") {
                        assertThat({ resolver.resolve(mounts) }, throws<VolumeMountResolutionException>(withMessage("Could not resolve volume mount path: expression 'the-original-invalid-expression' (evaluated as 'invalid') is not a valid path.")))
                    }
                }
            }

            given("a volume mount with an expression that cannot be evaluated") {
                val mounts by createForEachTest {
                    setOf(
                        LocalMount(EnvironmentVariableReference("DOES_NOT_EXIST", originalExpression = "the-original-expression"), relativeTo, "/container-1")
                    )
                }

                it("throws an appropriate exception") {
                    assertThat(
                        { resolver.resolve(mounts) },
                        throws<VolumeMountResolutionException>(withMessage("Could not resolve volume mount path: expression 'the-original-expression' could not be evaluated: The host environment variable 'DOES_NOT_EXIST' is not set, and no default value has been provided."))
                    )
                }
            }
        }

        describe("resolving cache mounts") {
            val cacheManager by createForEachTest {
                mock<CacheManager> {
                    on { projectCacheKey } doReturn "abc123"
                }
            }

            given("a cache mount") {
                val mounts = setOf(
                    CacheMount("cache-1", "/container-1"),
                    CacheMount("cache-2", "/container-2", "options-2")
                )

                given("the current cache type is volumes") {
                    beforeEachTest { whenever(cacheManager.cacheType) doReturn CacheType.Volume }

                    val resolver by createForEachTest { VolumeMountResolver(mock(), mock(), cacheManager, mock()) }

                    it("resolves the mount to a cache volume, preserving the container path and options") {
                        assertThat(
                            resolver.resolve(mounts), equalTo(
                                setOf(
                                    DockerVolumeMount(DockerVolumeMountSource.Volume("batect-cache-abc123-cache-1"), "/container-1"),
                                    DockerVolumeMount(DockerVolumeMountSource.Volume("batect-cache-abc123-cache-2"), "/container-2", "options-2")
                                )
                            )
                        )
                    }
                }

                given("the current cache type is directories") {
                    beforeEachTest { whenever(cacheManager.cacheType) doReturn CacheType.Directory }

                    val projectPaths by createForEachTest {
                        mock<ProjectPaths> {
                            on { cacheDirectory } doReturn fileSystem.getPath("/caches")
                        }
                    }

                    val resolver by createForEachTest { VolumeMountResolver(mock(), mock(), cacheManager, projectPaths) }
                    val resolvedMounts by createForEachTest { resolver.resolve(mounts) }

                    it("resolves the mount to a cache directory, preserving the container path and options") {
                        assertThat(
                            resolvedMounts, equalTo(
                                setOf(
                                    DockerVolumeMount(DockerVolumeMountSource.LocalPath("/caches/cache-1"), "/container-1"),
                                    DockerVolumeMount(DockerVolumeMountSource.LocalPath("/caches/cache-2"), "/container-2", "options-2")
                                )
                            )
                        )
                    }

                    it("creates each of the local cache directories") {
                        assertThat(Files.isDirectory(fileSystem.getPath("/caches/cache-1")), equalTo(true))
                        assertThat(Files.isDirectory(fileSystem.getPath("/caches/cache-2")), equalTo(true))
                    }
                }
            }
        }
    }
})
