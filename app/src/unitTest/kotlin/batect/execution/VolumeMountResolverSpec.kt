/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

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
import batect.config.TmpfsMount
import batect.dockerclient.HostMount
import batect.dockerclient.VolumeMount
import batect.dockerclient.VolumeReference
import batect.os.HostEnvironmentVariables
import batect.os.PathResolutionContext
import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.os.PathResolverFactory
import batect.os.PathType
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import okio.Path.Companion.toPath
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files

object VolumeMountResolverSpec : Spek({
    describe("a volume mount resolver") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }

        describe("resolving local mounts") {
            val context by createForEachTest { mock<PathResolutionContext>() }

            val pathResolver by createForEachTest {
                mock<PathResolver> {
                    on { resolve("file") } doReturn PathResolutionResult.Resolved("file", fileSystem.getPath("/resolved/file"), PathType.File, "described as file by resolver context")
                    on { resolve("directory") } doReturn PathResolutionResult.Resolved("directory", fileSystem.getPath("/resolved/directory"), PathType.Directory, "described as directory by resolver context")
                    on { resolve("other") } doReturn PathResolutionResult.Resolved("other", fileSystem.getPath("/resolved/other"), PathType.Other, "described as other by resolver context")
                    on { resolve("does-not-exist") } doReturn PathResolutionResult.Resolved("does-not-exist", fileSystem.getPath("/resolved/does-not-exist"), PathType.DoesNotExist, "described as does not exist by resolver context")
                    on { resolve("invalid") } doReturn PathResolutionResult.InvalidPath("invalid")
                }
            }

            val pathResolverFactory by createForEachTest {
                mock<PathResolverFactory> {
                    on { createResolver(context) } doReturn pathResolver
                }
            }

            val expressionEvaluationContext = ExpressionEvaluationContext(HostEnvironmentVariables("INVALID" to "invalid"), emptyMap())

            val resolver by createForEachTest { VolumeMountResolver(pathResolverFactory, expressionEvaluationContext, mock(), mock()) }

            given("a set of volume mounts from the configuration file that resolve to valid paths") {
                val mounts by createForEachTest {
                    setOf(
                        LocalMount(LiteralValue("file"), context, "/container-1"),
                        LocalMount(LiteralValue("directory"), context, "/container-2", "options-2"),
                        LocalMount(LiteralValue("other"), context, "/container-3"),
                        LocalMount(LiteralValue("does-not-exist"), context, "/container-4"),
                    )
                }

                it("resolves the local mount paths, preserving the container path and options") {
                    assertThat(
                        resolver.resolve(mounts),
                        equalTo(
                            setOf(
                                HostMount("/resolved/file".toPath(), "/container-1"),
                                HostMount("/resolved/directory".toPath(), "/container-2", "options-2"),
                                HostMount("/resolved/other".toPath(), "/container-3"),
                                HostMount("/resolved/does-not-exist".toPath(), "/container-4"),
                            ),
                        ),
                    )
                }
            }

            given("a volume mount for the Docker socket") {
                val mounts by createForEachTest {
                    setOf(
                        LocalMount(LiteralValue("/var/run/docker.sock"), context, "/container-1", "some-options"),
                    )
                }

                it("does not attempt to resolve the path and mounts it into the container") {
                    assertThat(
                        resolver.resolve(mounts),
                        equalTo(
                            setOf(
                                HostMount("/var/run/docker.sock".toPath(), "/container-1", "some-options"),
                            ),
                        ),
                    )
                }
            }

            given("a volume mount with an invalid path") {
                given("the path does not contain an expression") {
                    val mounts by createForEachTest {
                        setOf(
                            LocalMount(LiteralValue("invalid"), context, "/container-1"),
                        )
                    }

                    it("throws an appropriate exception") {
                        assertThat({ resolver.resolve(mounts) }, throws<VolumeMountResolutionException>(withMessage("Could not resolve volume mount path: 'invalid' is not a valid path.")))
                    }
                }

                given("the path contains an expression") {
                    val mounts by createForEachTest {
                        setOf(
                            LocalMount(EnvironmentVariableReference("INVALID", originalExpression = "the-original-invalid-expression"), context, "/container-1"),
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
                        LocalMount(EnvironmentVariableReference("DOES_NOT_EXIST", originalExpression = "the-original-expression"), context, "/container-1"),
                    )
                }

                it("throws an appropriate exception") {
                    assertThat(
                        { resolver.resolve(mounts) },
                        throws<VolumeMountResolutionException>(withMessage("Could not resolve volume mount path: expression 'the-original-expression' could not be evaluated: The host environment variable 'DOES_NOT_EXIST' is not set, and no default value has been provided.")),
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
                    CacheMount("cache-2", "/container-2", "options-2"),
                )

                given("the current cache type is volumes") {
                    beforeEachTest { whenever(cacheManager.cacheType) doReturn CacheType.Volume }

                    val resolver by createForEachTest { VolumeMountResolver(mock(), mock(), cacheManager, mock()) }

                    it("resolves the mount to a cache volume, preserving the container path and options") {
                        assertThat(
                            resolver.resolve(mounts),
                            equalTo(
                                setOf(
                                    VolumeMount(VolumeReference("batect-cache-abc123-cache-1"), "/container-1"),
                                    VolumeMount(VolumeReference("batect-cache-abc123-cache-2"), "/container-2", "options-2"),
                                ),
                            ),
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
                            resolvedMounts,
                            equalTo(
                                setOf(
                                    HostMount("/caches/cache-1".toPath(), "/container-1"),
                                    HostMount("/caches/cache-2".toPath(), "/container-2", "options-2"),
                                ),
                            ),
                        )
                    }

                    it("creates each of the local cache directories") {
                        assertThat(Files.isDirectory(fileSystem.getPath("/caches/cache-1")), equalTo(true))
                        assertThat(Files.isDirectory(fileSystem.getPath("/caches/cache-2")), equalTo(true))
                    }
                }
            }
        }

        describe("resolving tmpfs mounts") {
            val resolver by createForEachTest { VolumeMountResolver(mock(), mock(), mock(), mock()) }

            given("the mount has options specified") {
                val mount = TmpfsMount("/some/container/path", "some-options")
                val resolvedMounts by runForEachTest { resolver.resolve(setOf(mount)) }

                it("returns a tmpfs mount configuration") {
                    assertThat(resolvedMounts, equalTo(setOf(batect.dockerclient.TmpfsMount("/some/container/path", "some-options"))))
                }
            }

            given("the mount has no options specified") {
                val mount = TmpfsMount("/some/container/path")
                val resolvedMounts by runForEachTest { resolver.resolve(setOf(mount)) }

                it("returns a tmpfs mount configuration with no options") {
                    assertThat(resolvedMounts, equalTo(setOf(batect.dockerclient.TmpfsMount("/some/container/path", ""))))
                }
            }
        }
    }
})
