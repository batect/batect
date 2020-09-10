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

import batect.cli.CommandLineOptions
import batect.config.BuildImage
import batect.config.Configuration
import batect.config.Container
import batect.config.EnvironmentVariableReference
import batect.config.Expression
import batect.config.ExpressionEvaluationContext
import batect.config.ExpressionEvaluationException
import batect.config.ImagePullPolicy
import batect.config.LiteralValue
import batect.docker.DockerImage
import batect.docker.ImageBuildFailedException
import batect.docker.client.DockerImageBuildProgress
import batect.docker.client.DockerImagesClient
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
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.on
import batect.testutils.osIndependentPath
import batect.testutils.pathResolutionContextDoesNotMatter
import batect.ui.containerio.ContainerIOStreamingOptions
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import okio.Sink
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object BuildImageStepRunnerSpec : Spek({
    describe("running a 'build image' step") {
        val pathResolutionContext = pathResolutionContextDoesNotMatter()
        val buildDirectory = LiteralValue("/some-build-dir")
        val resolvedBuildDirectory = osIndependentPath("/resolved/build-dir")
        val buildArgs = mapOf("some_arg" to LiteralValue("some_value"), "SOME_PROXY_CONFIG" to LiteralValue("overridden"), "SOME_HOST_VAR" to EnvironmentVariableReference("SOME_ENV_VAR"))
        val dockerfilePath = "some-Dockerfile-path"
        val container = Container("some-container", BuildImage(buildDirectory, pathResolutionContext, buildArgs, dockerfilePath))
        val step = BuildImageStep(container)
        val outputSink by createForEachTest { mock<Sink>() }

        val config = Configuration("some-project")
        val imagesClient by createForEachTest { mock<DockerImagesClient>() }
        val proxyVariables = mapOf("SOME_PROXY_CONFIG" to "some_proxy", "SOME_OTHER_PROXY_CONFIG" to "some_other_value")
        val proxyEnvironmentVariablesProvider = mock<ProxyEnvironmentVariablesProvider> {
            on { getProxyEnvironmentVariables(emptySet()) } doReturn proxyVariables
        }

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

        val cancellationContext by createForEachTest { mock<CancellationContext>() }

        val ioStreamingOptions by createForEachTest {
            mock<ContainerIOStreamingOptions> {
                on { stdoutForImageBuild(container) } doReturn outputSink
            }
        }

        val systemInfo = mock<SystemInfo> {
            on { lineSeparator } doReturn "SYSTEM_LINE_SEPARATOR"
        }

        val expressionEvaluationContext = ExpressionEvaluationContext(HostEnvironmentVariables("SOME_ENV_VAR" to "some env var value"), emptyMap())
        val eventSink by createForEachTest { mock<TaskEventSink>() }

        val runner by createForEachTest {
            BuildImageStepRunner(
                config,
                imagesClient,
                proxyEnvironmentVariablesProvider,
                pathResolverFactory,
                expressionEvaluationContext,
                cancellationContext,
                ioStreamingOptions,
                CommandLineOptions(dontPropagateProxyEnvironmentVariables = false),
                systemInfo
            )
        }

        describe("when building the image succeeds") {
            on("and propagating proxy-related environment variables is enabled") {
                val image = DockerImage("some-image")
                val update1 = DockerImageBuildProgress(1, 2, "First step", null)
                val update2 = DockerImageBuildProgress(2, 2, "Second step", null)

                describe("regardless of the image pull policy") {
                    beforeEachTest {
                        whenever(imagesClient.build(eq(resolvedBuildDirectory), any(), eq(dockerfilePath), eq(pathResolutionContext), eq(setOf("some-project-some-container")), eq(false), eq(outputSink), eq(cancellationContext), any()))
                            .then { invocation ->
                                @Suppress("UNCHECKED_CAST")
                                val onStatusUpdate = invocation.arguments[8] as (DockerImageBuildProgress) -> Unit

                                onStatusUpdate(update1)
                                onStatusUpdate(update2)

                                image
                            }

                        runner.run(step, eventSink)
                    }

                    it("passes the image build args provided by the user as well as any proxy-related build args, with user-provided build args overriding the generated proxy-related build args, and with the cache setup command included") {
                        val expectedArgs = mapOf(
                            "some_arg" to "some_value",
                            "SOME_PROXY_CONFIG" to "overridden",
                            "SOME_OTHER_PROXY_CONFIG" to "some_other_value",
                            "SOME_HOST_VAR" to "some env var value"
                        )

                        verify(imagesClient).build(any(), eq(expectedArgs), any(), any(), any(), any(), any(), any(), any())
                    }

                    it("emits a 'image build progress' event for each update received from Docker") {
                        verify(eventSink).postEvent(ImageBuildProgressEvent(container, update1))
                        verify(eventSink).postEvent(ImageBuildProgressEvent(container, update2))
                    }

                    it("emits a 'image built' event") {
                        verify(eventSink).postEvent(ImageBuiltEvent(container, image))
                    }
                }

                given("the image pull policy is set to 'if not present'") {
                    val containerWithImagePullPolicy = Container("some-container", BuildImage(buildDirectory, pathResolutionContext, imagePullPolicy = ImagePullPolicy.IfNotPresent))
                    val stepWithImagePullPolicy = BuildImageStep(containerWithImagePullPolicy)

                    beforeEachTest {
                        whenever(imagesClient.build(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any())).doReturn(image)

                        runner.run(stepWithImagePullPolicy, eventSink)
                    }

                    it("calls the Docker API with forcibly pulling the image disabled") {
                        verify(imagesClient).build(any(), any(), any(), any(), any(), eq(false), anyOrNull(), any(), any())
                    }
                }

                given("the image pull policy is set to 'always'") {
                    val containerWithImagePullPolicy = Container("some-container", BuildImage(buildDirectory, pathResolutionContext, imagePullPolicy = ImagePullPolicy.Always))
                    val stepWithImagePullPolicy = BuildImageStep(containerWithImagePullPolicy)

                    beforeEachTest {
                        whenever(imagesClient.build(any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any())).doReturn(image)

                        runner.run(stepWithImagePullPolicy, eventSink)
                    }

                    it("calls the Docker API with forcibly pulling the image enabled") {
                        verify(imagesClient).build(any(), any(), any(), any(), any(), eq(true), anyOrNull(), any(), any())
                    }
                }
            }

            on("and propagating proxy-related environment variables is disabled") {
                val image = DockerImage("some-image")
                val commandLineOptionsWithProxyEnvironmentVariablePropagationDisabled = CommandLineOptions(dontPropagateProxyEnvironmentVariables = true)
                val runnerWithProxyEnvironmentVariablePropagationDisabled by createForEachTest {
                    BuildImageStepRunner(
                        config,
                        imagesClient,
                        proxyEnvironmentVariablesProvider,
                        pathResolverFactory,
                        expressionEvaluationContext,
                        cancellationContext,
                        ioStreamingOptions,
                        commandLineOptionsWithProxyEnvironmentVariablePropagationDisabled,
                        systemInfo
                    )
                }

                beforeEachTest {
                    whenever(imagesClient.build(eq(resolvedBuildDirectory), any(), eq(dockerfilePath), eq(pathResolutionContext), eq(setOf("some-project-some-container")), eq(false), eq(outputSink), eq(cancellationContext), any())).thenReturn(image)

                    runnerWithProxyEnvironmentVariablePropagationDisabled.run(step, eventSink)
                }

                it("does not pass the proxy-related environment variables as image build arguments, but does still pass the user-provided build args with any environment variable references resolved") {
                    val expectedArgs = mapOf(
                        "some_arg" to "some_value",
                        "SOME_PROXY_CONFIG" to "overridden",
                        "SOME_HOST_VAR" to "some env var value"
                    )

                    verify(imagesClient).build(any(), eq(expectedArgs), any(), any(), any(), any(), any(), any(), any())
                }

                it("emits a 'image built' event") {
                    verify(eventSink).postEvent(ImageBuiltEvent(container, image))
                }
            }
        }

        on("when building the image fails") {
            beforeEachTest {
                whenever(imagesClient.build(eq(resolvedBuildDirectory), any(), eq(dockerfilePath), eq(pathResolutionContext), eq(setOf("some-project-some-container")), eq(false), eq(outputSink), eq(cancellationContext), any())).thenThrow(ImageBuildFailedException("Something went wrong.\nMore details on this line."))
                runner.run(step, eventSink)
            }

            it("emits a 'image build failed' event with all line breaks replaced with the system line separator") {
                verify(eventSink).postEvent(ImageBuildFailedEvent(container, "Something went wrong.SYSTEM_LINE_SEPARATORMore details on this line."))
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
    }
})
