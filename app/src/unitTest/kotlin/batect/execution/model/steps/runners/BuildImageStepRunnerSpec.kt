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

package batect.execution.model.steps.runners

import batect.cli.CommandLineOptions
import batect.config.BuildImage
import batect.config.Container
import batect.config.EnvironmentVariableReference
import batect.config.Expression
import batect.config.ExpressionEvaluationContext
import batect.config.ExpressionEvaluationException
import batect.config.ImagePullPolicy
import batect.config.LiteralValue
import batect.config.TaskSpecialisedConfiguration
import batect.docker.ActiveImageBuildStep
import batect.docker.AggregatedImageBuildProgress
import batect.dockerclient.BuilderVersion
import batect.dockerclient.DockerClient
import batect.dockerclient.ImageBuildFailedException
import batect.dockerclient.ImageBuildProgressReceiver
import batect.dockerclient.ImageReference
import batect.dockerclient.StepStarting
import batect.dockerclient.io.SinkTextOutput
import batect.execution.model.events.ImageBuildFailedEvent
import batect.execution.model.events.ImageBuildProgressEvent
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.BuildImageStep
import batect.os.HostEnvironmentVariables
import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.os.PathResolverFactory
import batect.os.PathType
import batect.os.SystemInfo
import batect.primitives.CancellationContext
import batect.proxies.ProxyEnvironmentVariablesProvider
import batect.telemetry.TestTelemetryCaptor
import batect.testutils.beforeEachTestSuspend
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.itSuspend
import batect.testutils.on
import batect.testutils.pathResolutionContextDoesNotMatter
import batect.ui.containerio.ContainerIOStreamingOptions
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.hasSize
import okio.Buffer
import okio.Path.Companion.toOkioPath
import okio.buffer
import org.mockito.kotlin.any
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Paths

object BuildImageStepRunnerSpec : Spek({
    describe("running a 'build image' step") {
        val pathResolutionContext = pathResolutionContextDoesNotMatter()
        val buildDirectory = LiteralValue("/some-build-dir")
        val buildArgs = mapOf("some_arg" to LiteralValue("some_value"), "SOME_PROXY_CONFIG" to LiteralValue("overridden"), "SOME_HOST_VAR" to EnvironmentVariableReference("SOME_ENV_VAR"))
        val dockerfilePath = "some-Dockerfile"
        val targetStage = "some-target-stage"
        val container = Container("some-container", BuildImage(buildDirectory, pathResolutionContext, buildArgs, dockerfilePath, targetStage = targetStage))
        val step = BuildImageStep(container)
        val outputSink by createForEachTest { Buffer() }
        val builderVersion = BuilderVersion.BuildKit

        val config = TaskSpecialisedConfiguration("some-project")
        val dockerClient by createForEachTest { mock<DockerClient>() }
        val proxyVariables = mapOf("SOME_PROXY_CONFIG" to "some_proxy", "SOME_OTHER_PROXY_CONFIG" to "some_other_value")
        val proxyEnvironmentVariablesProvider = mock<ProxyEnvironmentVariablesProvider> {
            on { getProxyEnvironmentVariables(emptySet()) } doReturn proxyVariables
        }

        val resolvedBuildDirectory = Paths.get("src", "unitTest", "kotlin", "batect", "execution", "model", "steps", "runners", "test-fixtures", "build-context").toAbsolutePath()
        val resolvedDockerfile = resolvedBuildDirectory.resolve(dockerfilePath)

        val pathResolver by createForEachTest {
            mock<PathResolver> {
                on { resolve(buildDirectory.value) } doReturn PathResolutionResult.Resolved("/some-build-dir", resolvedBuildDirectory, PathType.Directory, "described as '$resolvedBuildDirectory'")
            }
        }

        val pathResolverFactory by createForEachTest {
            mock<PathResolverFactory> {
                on { createResolver(pathResolutionContext) } doReturn pathResolver
            }
        }

        val cancellationContext by createForEachTest { CancellationContext() }

        val ioStreamingOptions by createForEachTest {
            mock<ContainerIOStreamingOptions> {
                on { stdoutForImageBuild(container) } doReturn outputSink
            }
        }

        val systemInfo = mock<SystemInfo> {
            on { lineSeparator } doReturn "SYSTEM_LINE_SEPARATOR"
        }

        val telemetryCaptor by createForEachTest { TestTelemetryCaptor() }
        val expressionEvaluationContext = ExpressionEvaluationContext(HostEnvironmentVariables("SOME_ENV_VAR" to "some env var value"), emptyMap())
        val eventSink by createForEachTest { mock<TaskEventSink>() }
        val commandLineOptions = CommandLineOptions(dontPropagateProxyEnvironmentVariables = false, imageTags = mapOf(container.name to setOf("some-extra-image-tag")))
        val logger by createLoggerForEachTest()

        val runner by createForEachTest {
            BuildImageStepRunner(
                config,
                dockerClient,
                proxyEnvironmentVariablesProvider,
                pathResolverFactory,
                expressionEvaluationContext,
                cancellationContext,
                ioStreamingOptions,
                commandLineOptions,
                builderVersion,
                systemInfo,
                telemetryCaptor,
                logger
            )
        }

        describe("when building the image succeeds") {
            on("and propagating proxy-related environment variables is enabled") {
                val image = ImageReference("some-image")

                describe("regardless of the image pull policy") {
                    beforeEachTestSuspend {
                        whenever(dockerClient.buildImage(any(), any(), any()))
                            .then { invocation ->
                                val output = invocation.getArgument<SinkTextOutput>(1)

                                output.sink.buffer().use { buffer ->
                                    buffer.writeUtf8("Some output from the build process\nAnother line of output")
                                }

                                val onStatusUpdate = invocation.getArgument<ImageBuildProgressReceiver>(2)
                                onStatusUpdate(StepStarting(1, "First step"))

                                image
                            }

                        runner.run(step, eventSink)
                    }

                    itSuspend("runs the build with the resolved build directory") {
                        verify(dockerClient).buildImage(argWhere { it.contextDirectory == resolvedBuildDirectory.toOkioPath() }, any(), any())
                    }

                    itSuspend("runs the build with the resolved Dockerfile") {
                        verify(dockerClient).buildImage(argWhere { it.pathToDockerfile == resolvedDockerfile.toOkioPath() }, any(), any())
                    }

                    itSuspend("runs the build with the specified target stage") {
                        verify(dockerClient).buildImage(argWhere { it.targetBuildStage == targetStage }, any(), any())
                    }

                    itSuspend("generates a tag for the image based on the project and container names, and includes any additional image tags provided on the command line") {
                        verify(dockerClient).buildImage(argWhere { it.imageTags == setOf("some-project-some-container", "some-extra-image-tag") }, any(), any())
                    }

                    it("propagates output to the provided stdout stream") {
                        assertThat(outputSink.readUtf8(), equalTo("Some output from the build process\nAnother line of output"))
                    }

                    itSuspend("runs the build with the specified image builder") {
                        verify(dockerClient).buildImage(argWhere { it.builder == builderVersion }, any(), any())
                    }

                    itSuspend("passes the image build args provided by the user as well as any proxy-related build args, with user-provided build args overriding the generated proxy-related build args, and with the cache setup command included") {
                        val expectedArgs = mapOf(
                            "some_arg" to "some_value",
                            "SOME_PROXY_CONFIG" to "overridden",
                            "SOME_OTHER_PROXY_CONFIG" to "some_other_value",
                            "SOME_HOST_VAR" to "some env var value"
                        )

                        verify(dockerClient).buildImage(argWhere { it.buildArgs == expectedArgs }, any(), any())
                    }

                    it("emits a 'image build progress' event for each update received from Docker") {
                        verify(eventSink).postEvent(ImageBuildProgressEvent(container, AggregatedImageBuildProgress(setOf(ActiveImageBuildStep.NotDownloading(1, "First step")))))
                    }

                    it("emits a 'image built' event") {
                        verify(eventSink).postEvent(ImageBuiltEvent(container, image))
                    }

                    it("does not emit any 'image build failed' events") {
                        verify(eventSink, never()).postEvent(isA<ImageBuildFailedEvent>())
                    }

                    it("records a span in telemetry for the image build") {
                        assertThat(telemetryCaptor.allSpans, hasSize(equalTo(1)))

                        val span = telemetryCaptor.allSpans.single()
                        assertThat(span.type, equalTo("BuildImage"))
                    }
                }

                given("the image pull policy is set to 'if not present'") {
                    val containerWithImagePullPolicy = Container("some-container", BuildImage(buildDirectory, pathResolutionContext, dockerfilePath = dockerfilePath, imagePullPolicy = ImagePullPolicy.IfNotPresent))
                    val stepWithImagePullPolicy = BuildImageStep(containerWithImagePullPolicy)

                    beforeEachTestSuspend {
                        whenever(dockerClient.buildImage(any(), any(), any())).thenReturn(image)

                        runner.run(stepWithImagePullPolicy, eventSink)
                    }

                    itSuspend("calls the Docker API with forcibly pulling the image disabled") {
                        verify(dockerClient).buildImage(argWhere { it.alwaysPullBaseImages == false }, any(), any())
                    }

                    it("does not emit any 'image build failed' events") {
                        verify(eventSink, never()).postEvent(isA<ImageBuildFailedEvent>())
                    }
                }

                given("the image pull policy is set to 'always'") {
                    val containerWithImagePullPolicy = Container("some-container", BuildImage(buildDirectory, pathResolutionContext, dockerfilePath = dockerfilePath, imagePullPolicy = ImagePullPolicy.Always))
                    val stepWithImagePullPolicy = BuildImageStep(containerWithImagePullPolicy)

                    beforeEachTestSuspend {
                        whenever(dockerClient.buildImage(any(), any(), any())).thenReturn(image)

                        runner.run(stepWithImagePullPolicy, eventSink)
                    }

                    itSuspend("calls the Docker API with forcibly pulling the image enabled") {
                        verify(dockerClient).buildImage(argWhere { it.alwaysPullBaseImages == true }, any(), any())
                    }

                    it("does not emit any 'image build failed' events") {
                        verify(eventSink, never()).postEvent(isA<ImageBuildFailedEvent>())
                    }
                }
            }

            on("and propagating proxy-related environment variables is disabled") {
                val image = ImageReference("some-image")
                val commandLineOptionsWithProxyEnvironmentVariablePropagationDisabled = CommandLineOptions(dontPropagateProxyEnvironmentVariables = true)
                val runnerWithProxyEnvironmentVariablePropagationDisabled by createForEachTest {
                    BuildImageStepRunner(
                        config,
                        dockerClient,
                        proxyEnvironmentVariablesProvider,
                        pathResolverFactory,
                        expressionEvaluationContext,
                        cancellationContext,
                        ioStreamingOptions,
                        commandLineOptionsWithProxyEnvironmentVariablePropagationDisabled,
                        builderVersion,
                        systemInfo,
                        telemetryCaptor,
                        logger
                    )
                }

                beforeEachTestSuspend {
                    whenever(dockerClient.buildImage(any(), any(), any())).thenReturn(image)

                    runnerWithProxyEnvironmentVariablePropagationDisabled.run(step, eventSink)
                }

                itSuspend("does not pass the proxy-related environment variables as image build arguments, but does still pass the user-provided build args with any environment variable references resolved") {
                    val expectedArgs = mapOf(
                        "some_arg" to "some_value",
                        "SOME_PROXY_CONFIG" to "overridden",
                        "SOME_HOST_VAR" to "some env var value"
                    )

                    verify(dockerClient).buildImage(argWhere { it.buildArgs == expectedArgs }, any(), any())
                }

                it("emits a 'image built' event") {
                    verify(eventSink).postEvent(ImageBuiltEvent(container, image))
                }

                it("does not emit any 'image build failed' events") {
                    verify(eventSink, never()).postEvent(isA<ImageBuildFailedEvent>())
                }
            }
        }

        on("when building the image fails") {
            on("when the build does not produce any output") {
                beforeEachTestSuspend {
                    whenever(dockerClient.buildImage(any(), any(), any())).thenThrow(ImageBuildFailedException("Something went wrong."))

                    runner.run(step, eventSink)
                }

                it("emits a 'image build failed' event") {
                    verify(eventSink).postEvent(ImageBuildFailedEvent(container, "Something went wrong."))
                }
            }

            on("when the build does produce some output") {
                beforeEachTestSuspend {
                    whenever(dockerClient.buildImage(any(), any(), any())).thenAnswer { invocation ->
                        val output = invocation.getArgument<SinkTextOutput>(1)
                        output.sink.buffer().use { buffer ->
                            buffer.writeUtf8("Some output from the build process\nAnother line of output")
                        }

                        throw ImageBuildFailedException("Something went wrong.")
                    }

                    runner.run(step, eventSink)
                }

                it("emits a 'image build failed' event with all line breaks replaced with the system line separator") {
                    verify(eventSink).postEvent(ImageBuildFailedEvent(container, "Something went wrong. Output from Docker was:SYSTEM_LINE_SEPARATORSome output from the build processSYSTEM_LINE_SEPARATORAnother line of output"))
                }
            }
        }

        on("when a build arg is an invalid reference") {
            val invalidReference = mock<Expression> {
                on { evaluate(expressionEvaluationContext) } doThrow ExpressionEvaluationException("Couldn't evaluate expression.")
            }

            val imageSourceWithInvalidBuildArgReference = BuildImage(buildDirectory, pathResolutionContext, mapOf("SOME_HOST_VAR" to invalidReference), dockerfilePath)
            val containerWithInvalidBuildArgReference = Container("some-container", imageSourceWithInvalidBuildArgReference)

            val stepWithInvalidBuildArgReference = BuildImageStep(containerWithInvalidBuildArgReference)

            beforeEachTest {
                runner.run(stepWithInvalidBuildArgReference, eventSink)
            }

            it("emits a 'image build failed' event") {
                verify(eventSink).postEvent(ImageBuildFailedEvent(containerWithInvalidBuildArgReference, "The value for the build arg 'SOME_HOST_VAR' cannot be evaluated: Couldn't evaluate expression."))
            }
        }

        on("when the build directory is not a directory") {
            beforeEachTest {
                whenever(pathResolver.resolve(buildDirectory.value)).doReturn(PathResolutionResult.Resolved(buildDirectory.value, resolvedBuildDirectory, PathType.File, "described as 'a file'"))
                runner.run(step, eventSink)
            }

            it("emits a 'image build failed' event") {
                verify(eventSink).postEvent(ImageBuildFailedEvent(container, "Build directory '/some-build-dir' (described as 'a file') is not a directory."))
            }
        }

        on("when the build directory does not exist") {
            beforeEachTest {
                whenever(pathResolver.resolve(buildDirectory.value)).doReturn(PathResolutionResult.Resolved(buildDirectory.value, resolvedBuildDirectory, PathType.DoesNotExist, "described as 'a non-existent thing'"))
                runner.run(step, eventSink)
            }

            it("emits a 'image build failed' event") {
                verify(eventSink).postEvent(ImageBuildFailedEvent(container, "Build directory '/some-build-dir' (described as 'a non-existent thing') does not exist."))
            }
        }

        on("when the build directory is an invalid path") {
            beforeEachTest {
                whenever(pathResolver.resolve(buildDirectory.value)).doReturn(PathResolutionResult.InvalidPath(buildDirectory.value))
                runner.run(step, eventSink)
            }

            it("emits a 'image build failed' event") {
                verify(eventSink).postEvent(ImageBuildFailedEvent(container, "Build directory '/some-build-dir' is not a valid path."))
            }
        }

        on("when the build directory expression cannot be evaluated") {
            val invalidReference = mock<Expression> {
                on { evaluate(expressionEvaluationContext) } doThrow ExpressionEvaluationException("Couldn't evaluate expression.")
            }

            val imageSourceWithInvalidBuildDirectory = BuildImage(invalidReference, pathResolutionContext)
            val containerWithInvalidBuildDirectory = Container("some-container", imageSourceWithInvalidBuildDirectory)
            val stepWithInvalidBuildDirectory = BuildImageStep(containerWithInvalidBuildDirectory)

            beforeEachTest {
                runner.run(stepWithInvalidBuildDirectory, eventSink)
            }

            it("emits a 'image build failed' event") {
                verify(eventSink).postEvent(ImageBuildFailedEvent(containerWithInvalidBuildDirectory, "The value for the build directory cannot be evaluated: Couldn't evaluate expression."))
            }
        }

        describe("when the Dockerfile path is not valid") {
            beforeEachTest {
                whenever(pathResolutionContext.getPathForDisplay(any())).doReturn("(a description of the build directory)")
            }

            on("when the Dockerfile does not exist") {
                val containerWithNonExistentDockerfile = Container("some-container", BuildImage(buildDirectory, pathResolutionContext, dockerfilePath = "this-dockerfile-does-not-exist"))
                val stepWithNonExistentDockerfile = BuildImageStep(containerWithNonExistentDockerfile)

                beforeEachTest {
                    runner.run(stepWithNonExistentDockerfile, eventSink)
                }

                it("emits a 'image build failed' event") {
                    verify(eventSink).postEvent(ImageBuildFailedEvent(containerWithNonExistentDockerfile, "The Dockerfile 'this-dockerfile-does-not-exist' does not exist in the build directory (a description of the build directory)"))
                }
            }

            on("when the Dockerfile is not a file") {
                val containerWithNonFileDockerfile = Container("some-container", BuildImage(buildDirectory, pathResolutionContext, dockerfilePath = "../build-context"))
                val stepWithNonFileDockerfile = BuildImageStep(containerWithNonFileDockerfile)

                beforeEachTest {
                    runner.run(stepWithNonFileDockerfile, eventSink)
                }

                it("emits a 'image build failed' event") {
                    verify(eventSink).postEvent(ImageBuildFailedEvent(containerWithNonFileDockerfile, "The Dockerfile '../build-context' is not a file."))
                }
            }

            on("when the Dockerfile is not in the context directory") {
                val containerWithExternalDockerfile = Container("some-container", BuildImage(buildDirectory, pathResolutionContext, dockerfilePath = "../Dockerfile-outside-context"))
                val stepWithExternalDockerfile = BuildImageStep(containerWithExternalDockerfile)

                beforeEachTest {
                    runner.run(stepWithExternalDockerfile, eventSink)
                }

                it("emits a 'image build failed' event") {
                    verify(eventSink).postEvent(ImageBuildFailedEvent(containerWithExternalDockerfile, "The Dockerfile '../Dockerfile-outside-context' is not a child of the build directory (a description of the build directory)"))
                }
            }
        }
    }
})
