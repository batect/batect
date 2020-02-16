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

import batect.config.EnvironmentVariableReference
import batect.config.LiteralValue
import batect.config.VolumeMount
import batect.docker.DockerVolumeMount
import batect.os.HostEnvironmentVariables
import batect.os.PathResolutionResult
import batect.os.PathResolver
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
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object VolumeMountResolverSpec : Spek({
    describe("a volume mount resolver") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }

        val pathResolver by createForEachTest {
            mock<PathResolver> {
                on { resolve("file") } doReturn PathResolutionResult.Resolved("file", fileSystem.getPath("/resolved/file"), PathType.File)
                on { resolve("directory") } doReturn PathResolutionResult.Resolved("directory", fileSystem.getPath("/resolved/directory"), PathType.Directory)
                on { resolve("other") } doReturn PathResolutionResult.Resolved("other", fileSystem.getPath("/resolved/other"), PathType.Other)
                on { resolve("does-not-exist") } doReturn PathResolutionResult.Resolved("does-not-exist", fileSystem.getPath("/resolved/does-not-exist"), PathType.DoesNotExist)
                on { resolve("invalid") } doReturn PathResolutionResult.InvalidPath("invalid")
            }
        }

        val environmentVariables = HostEnvironmentVariables("INVALID" to "invalid")

        val resolver by createForEachTest { VolumeMountResolver(pathResolver, environmentVariables, mock()) }

        given("a set of volume mounts from the configuration file that resolve to valid paths") {
            val mounts = setOf(
                VolumeMount(LiteralValue("file"), "/container-1"),
                VolumeMount(LiteralValue("directory"), "/container-2", "options-2"),
                VolumeMount(LiteralValue("other"), "/container-3"),
                VolumeMount(LiteralValue("does-not-exist"), "/container-4")
            )

            it("resolves the local mount paths, preserving the container path and options") {
                assertThat(resolver.resolve(mounts), equalTo(setOf(
                    DockerVolumeMount("/resolved/file", "/container-1"),
                    DockerVolumeMount("/resolved/directory", "/container-2", "options-2"),
                    DockerVolumeMount("/resolved/other", "/container-3"),
                    DockerVolumeMount("/resolved/does-not-exist", "/container-4")
                )))
            }
        }

        given("a volume mount with an invalid path") {
            given("the path does not contain an expression") {
                val mounts = setOf(
                    VolumeMount(LiteralValue("invalid"), "/container-1")
                )

                it("throws an appropriate exception") {
                    assertThat({ resolver.resolve(mounts) }, throws<VolumeMountResolutionException>(withMessage("Could not resolve volume mount path: 'invalid' is not a valid path.")))
                }
            }

            given("the path contains an expression") {
                val mounts = setOf(
                    VolumeMount(EnvironmentVariableReference("INVALID", originalExpression = "the-original-invalid-expression"), "/container-1")
                )

                it("throws an appropriate exception") {
                    assertThat({ resolver.resolve(mounts) }, throws<VolumeMountResolutionException>(withMessage("Could not resolve volume mount path: expression 'the-original-invalid-expression' (evaluated as 'invalid') is not a valid path.")))
                }
            }
        }

        given("a volume mount with an expression that cannot be evaluated") {
            val mounts = setOf(
                VolumeMount(EnvironmentVariableReference("DOES_NOT_EXIST", originalExpression = "the-original-expression"), "/container-1")
            )

            it("throws an appropriate exception") {
                assertThat({ resolver.resolve(mounts) }, throws<VolumeMountResolutionException>(withMessage("Could not resolve volume mount path: expression 'the-original-expression' could not be evaluated: The host environment variable 'DOES_NOT_EXIST' is not set, and no default value has been provided.")))
            }
        }
    }
})
