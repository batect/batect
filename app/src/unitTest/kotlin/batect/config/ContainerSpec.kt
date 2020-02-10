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

package batect.config

import batect.config.io.deserializers.PathDeserializer
import batect.docker.Capability
import batect.os.Command
import batect.os.PathResolutionResult
import batect.os.PathType
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.osIndependentPath
import batect.testutils.runForEachTest
import batect.testutils.withColumn
import batect.testutils.withLineNumber
import batect.testutils.withMessage
import com.charleskorn.kaml.Yaml
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import kotlinx.serialization.ElementValueDecoder
import kotlinx.serialization.modules.serializersModuleOf
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration

object ContainerSpec : Spek({
    describe("a container") {
        val pathDeserializer by createForEachTest {
            mock<PathDeserializer> {
                on { deserialize(any()) } doAnswer { invocation ->
                    val input = invocation.arguments[0] as ElementValueDecoder

                    when (val path = input.decodeString()) {
                        "/does_not_exist" -> PathResolutionResult.Resolved(path, osIndependentPath("/some_resolved_path"), PathType.DoesNotExist)
                        "/file" -> PathResolutionResult.Resolved(path, osIndependentPath("/some_resolved_path"), PathType.File)
                        "/not_file_or_directory" -> PathResolutionResult.Resolved(path, osIndependentPath("/some_resolved_path"), PathType.Other)
                        "/invalid" -> PathResolutionResult.InvalidPath(path)
                        else -> PathResolutionResult.Resolved(path, osIndependentPath("/resolved" + path), PathType.Directory)
                    }
                }
            }
        }

        val parser by createForEachTest { Yaml(context = serializersModuleOf(PathResolutionResult::class, pathDeserializer)) }

        given("the config file has just a build directory") {
            given("and that directory exists") {
                val yaml = "build_directory: /some_build_dir"

                on("loading the configuration from the config file") {
                    val result by runForEachTest { parser.parse(Container.Companion, yaml) }

                    it("returns the expected container configuration, with the build directory resolved to an absolute path") {
                        assertThat(result, equalTo(Container("UNNAMED-FROM-CONFIG-FILE", BuildImage(osIndependentPath("/resolved/some_build_dir"), emptyMap(), "Dockerfile"))))
                    }
                }
            }

            data class BuildDirectoryResolutionTestCase(val description: String, val originalPath: String, val expectedMessage: String)

            setOf(
                BuildDirectoryResolutionTestCase(
                    "does not exist",
                    "/does_not_exist",
                    "Build directory '/does_not_exist' (resolved to '/some_resolved_path') does not exist."
                ),
                BuildDirectoryResolutionTestCase(
                    "is a file",
                    "/file",
                    "Build directory '/file' (resolved to '/some_resolved_path') is not a directory."
                ),
                BuildDirectoryResolutionTestCase(
                    "is neither a file or directory",
                    "/not_file_or_directory",
                    "Build directory '/not_file_or_directory' (resolved to '/some_resolved_path') is not a directory."
                ),
                BuildDirectoryResolutionTestCase(
                    "is an invalid path",
                    "/invalid",
                    "Build directory '/invalid' is not a valid path."
                )
            ).forEach { (description, originalPath, expectedMessage) ->
                given("and that path $description") {
                    val yaml = "build_directory: $originalPath"

                    on("loading the configuration from the config file") {
                        it("throws an appropriate exception") {
                            assertThat(
                                { parser.parse(Container.Companion, yaml) },
                                throws(withMessage(expectedMessage) and withLineNumber(1) and withColumn(1))
                            )
                        }
                    }
                }
            }
        }

        given("the config file has just an image") {
            val yaml = "image: some_image"

            on("loading the configuration from the config file") {
                val result by runForEachTest { parser.parse(Container.Companion, yaml) }

                it("returns the expected container configuration") {
                    assertThat(result, equalTo(Container("UNNAMED-FROM-CONFIG-FILE", PullImage("some_image"))))
                }
            }
        }

        given("the config file has both a build directory and an image") {
            val yaml = """
                image: some_image
                build_directory: /build_dir
            """.trimIndent()

            on("loading the configuration from the config file") {
                it("throws an appropriate exception") {
                    assertThat(
                        { parser.parse(Container.Companion, yaml) },
                        throws(withMessage("Only one of build_directory or image can be specified for a container, but both have been provided for this container.") and withLineNumber(1) and withColumn(1))
                    )
                }
            }
        }

        given("the config file has both an image and build args") {
            val yaml = """
                image: some_image
                build_args:
                  SOME_ARG: some_value
            """.trimIndent()

            on("loading the configuration from the config file") {
                it("throws an appropriate exception") {
                    assertThat(
                        { parser.parse(Container.Companion, yaml) },
                        throws(withMessage("build_args cannot be used with image, but both have been provided.") and withLineNumber(1) and withColumn(1))
                    )
                }
            }
        }

        given("the config file has both an image and a Dockerfile") {
            val yaml = """
                image: some_image
                dockerfile: some-Dockerfile
            """.trimIndent()

            on("loading the configuration from the config file") {
                it("throws an appropriate exception") {
                    assertThat(
                        { parser.parse(Container.Companion, yaml) },
                        throws(withMessage("dockerfile cannot be used with image, but both have been provided.") and withLineNumber(1) and withColumn(1))
                    )
                }
            }
        }

        given("the config file has neither a build directory nor an image") {
            val yaml = """
                command: do-the-thing
            """.trimIndent()

            on("loading the configuration from the config file") {
                it("throws an appropriate exception") {
                    assertThat(
                        { parser.parse(Container.Companion, yaml) },
                        throws(withMessage("One of either build_directory or image must be specified for each container, but neither have been provided for this container.") and withLineNumber(1) and withColumn(1))
                    )
                }
            }
        }

        given("the config file has all optional fields specified") {
            val yaml = """
                build_directory: /container-1-build-dir
                build_args:
                  SOME_ARG: some_value
                  SOME_DYNAMIC_VALUE: ${'$'}host_var
                dockerfile: some-Dockerfile
                command: do-the-thing.sh some-param
                entrypoint: sh
                environment:
                  OPTS: -Dthing
                  INT_VALUE: 1
                  FLOAT_VALUE: 12.6000
                  BOOL_VALUE: true
                  OTHER_VALUE: "the value"
                working_directory: /here
                volumes:
                  - /volume1:/here
                  - /somewhere:/else:ro
                devices:
                  - /dev/ttyUSB0:/dev/ttyUSB0
                  - /dev/sda:/dev/xvdc:r
                ports:
                  - "1234:5678"
                  - "9012:3456"
                health_check:
                  interval: 2s
                  retries: 10
                  start_period: 1s
                run_as_current_user:
                  enabled: true
                  home_directory: /home/something
                privileged: true
                enable_init_process: true
                capabilities_to_add:
                  - NET_ADMIN
                capabilities_to_drop:
                  - KILL
                additional_hostnames:
                  - extra-name
                setup_commands:
                  - command: /do/the/thing.sh
                  - command: /some/other/thing.sh
                    working_directory: /some/dir
            """.trimIndent()

            on("loading the configuration from the config file") {
                val result by runForEachTest { parser.parse(Container.Companion, yaml) }

                it("returns the expected container configuration") {
                    assertThat(result.imageSource, equalTo(BuildImage(osIndependentPath("/resolved/container-1-build-dir"), mapOf("SOME_ARG" to LiteralValue("some_value"), "SOME_DYNAMIC_VALUE" to EnvironmentVariableReference("host_var")), "some-Dockerfile")))
                    assertThat(result.command, equalTo(Command.parse("do-the-thing.sh some-param")))
                    assertThat(result.entrypoint, equalTo(Command.parse("sh")))
                    assertThat(
                        result.environment, equalTo(
                            mapOf(
                                "OPTS" to LiteralValue("-Dthing"),
                                "INT_VALUE" to LiteralValue("1"),
                                "FLOAT_VALUE" to LiteralValue("12.6000"),
                                "BOOL_VALUE" to LiteralValue("true"),
                                "OTHER_VALUE" to LiteralValue("the value")
                            )
                        )
                    )
                    assertThat(result.workingDirectory, equalTo("/here"))
                    assertThat(
                        result.volumeMounts, equalTo(
                            setOf(
                                VolumeMount("/resolved/volume1", "/here", null),
                                VolumeMount("/resolved/somewhere", "/else", "ro")
                            )
                        )
                    )
                    assertThat(
                        result.deviceMounts, equalTo(
                            setOf(
                                DeviceMount("/dev/ttyUSB0", "/dev/ttyUSB0", null),
                                DeviceMount("/dev/sda", "/dev/xvdc", "r")
                            )
                        )
                    )
                    assertThat(result.portMappings, equalTo(setOf(PortMapping(1234, 5678), PortMapping(9012, 3456))))
                    assertThat(result.healthCheckConfig, equalTo(HealthCheckConfig(Duration.ofSeconds(2), 10, Duration.ofSeconds(1))))
                    assertThat(result.runAsCurrentUserConfig, equalTo(RunAsCurrentUserConfig.RunAsCurrentUser("/home/something")))
                    assertThat(result.privileged, equalTo(true))
                    assertThat(result.enableInitProcess, equalTo(true))
                    assertThat(result.capabilitiesToAdd, equalTo(setOf(Capability.NET_ADMIN)))
                    assertThat(result.capabilitiesToDrop, equalTo(setOf(Capability.KILL)))
                    assertThat(result.additionalHostnames, equalTo(setOf("extra-name")))
                    assertThat(result.setupCommands, equalTo(listOf(
                        SetupCommand(Command.parse("/do/the/thing.sh")),
                        SetupCommand(Command.parse("/some/other/thing.sh"), "/some/dir")
                    )))
                }
            }
        }
    }
})
