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

import batect.config.BuildImage
import batect.config.Container
import batect.config.EnvironmentVariableReference
import batect.config.LiteralValue
import batect.config.Expression
import batect.config.ExpressionEvaluationContext
import batect.config.ExpressionEvaluationException
import batect.docker.DockerImage
import batect.docker.ImageBuildFailedException
import batect.docker.client.DockerImageBuildProgress
import batect.docker.client.DockerImagesClient
import batect.execution.CancellationContext
import batect.execution.CleanupOption
import batect.execution.RunOptions
import batect.execution.model.events.ImageBuildFailedEvent
import batect.execution.model.events.ImageBuildProgressEvent
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.BuildImageStep
import batect.os.HostEnvironmentVariables
import batect.os.SystemInfo
import batect.os.proxies.ProxyEnvironmentVariablesProvider
import batect.testutils.createForEachTest
import batect.testutils.on
import batect.ui.containerio.ContainerIOStreamingOptions
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import okio.Sink
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Paths

object BuildImageStepRunnerSpec : Spek({
    describe("running a 'build image' step") {
        val buildDirectory = Paths.get("/some-build-dir")
        val buildArgs = mapOf("some_arg" to LiteralValue("some_value"), "SOME_PROXY_CONFIG" to LiteralValue("overridden"), "SOME_HOST_VAR" to EnvironmentVariableReference("SOME_ENV_VAR"))
        val dockerfilePath = "some-Dockerfile-path"
        val imageTag = "some_image_tag"
        val container = Container("some-container", BuildImage(buildDirectory, buildArgs, dockerfilePath))
        val step = BuildImageStep(container, imageTag)
        val outputSink by createForEachTest { mock<Sink>() }

        val imagesClient by createForEachTest { mock<DockerImagesClient>() }
        val proxyVariables = mapOf("SOME_PROXY_CONFIG" to "some_proxy", "SOME_OTHER_PROXY_CONFIG" to "some_other_value")
        val proxyEnvironmentVariablesProvider = mock<ProxyEnvironmentVariablesProvider> {
            on { getProxyEnvironmentVariables(emptySet()) } doReturn proxyVariables
        }

        val cancellationContext by createForEachTest { mock<CancellationContext>() }

        val ioStreamingOptions by createForEachTest {
            mock<ContainerIOStreamingOptions> {
                on { stdoutForImageBuild(container) } doReturn outputSink
            }
        }

        val runOptions = RunOptions("some-task", emptyList(), CleanupOption.Cleanup, CleanupOption.Cleanup, true, emptyMap())
        val systemInfo = mock<SystemInfo> {
            on { lineSeparator } doReturn "SYSTEM_LINE_SEPARATOR"
        }

        val expressionEvaluationContext = ExpressionEvaluationContext(HostEnvironmentVariables("SOME_ENV_VAR" to "some env var value"), emptyMap())
        val eventSink by createForEachTest { mock<TaskEventSink>() }

        val runner by createForEachTest {
            BuildImageStepRunner(
                imagesClient,
                proxyEnvironmentVariablesProvider,
                expressionEvaluationContext,
                cancellationContext,
                ioStreamingOptions,
                runOptions,
                systemInfo
            )
        }

        describe("when building the image succeeds") {
            on("and propagating proxy-related environment variables is enabled") {
                val image = DockerImage("some-image")
                val update1 = DockerImageBuildProgress(1, 2, "First step", null)
                val update2 = DockerImageBuildProgress(2, 2, "Second step", null)

                beforeEachTest {
                    whenever(imagesClient.build(eq(buildDirectory), any(), eq(dockerfilePath), eq(setOf(imageTag)), eq(outputSink), eq(cancellationContext), any()))
                        .then { invocation ->
                            @Suppress("UNCHECKED_CAST")
                            val onStatusUpdate = invocation.arguments[6] as (DockerImageBuildProgress) -> Unit

                            onStatusUpdate(update1)
                            onStatusUpdate(update2)

                            image
                        }

                    runner.run(step, eventSink)
                }

                it("passes the image build args provided by the user as well as any proxy-related build args, with user-provided build args overriding the generated proxy-related build args, and with any environment variable references resolved") {
                    val expectedArgs = mapOf(
                        "some_arg" to "some_value",
                        "SOME_PROXY_CONFIG" to "overridden",
                        "SOME_OTHER_PROXY_CONFIG" to "some_other_value",
                        "SOME_HOST_VAR" to "some env var value"
                    )

                    verify(imagesClient).build(any(), eq(expectedArgs), any(), any(), any(), any(), any())
                }

                it("emits a 'image build progress' event for each update received from Docker") {
                    verify(eventSink).postEvent(ImageBuildProgressEvent(container, update1))
                    verify(eventSink).postEvent(ImageBuildProgressEvent(container, update2))
                }

                it("emits a 'image built' event") {
                    verify(eventSink).postEvent(ImageBuiltEvent(container, image))
                }
            }

            on("and propagating proxy-related environment variables is disabled") {
                val image = DockerImage("some-image")
                val runOptionsWithProxyEnvironmentVariablePropagationDisabled = runOptions.copy(propagateProxyEnvironmentVariables = false)
                val runnerWithProxyEnvironmentVariablePropagationDisabled by createForEachTest {
                    BuildImageStepRunner(
                        imagesClient,
                        proxyEnvironmentVariablesProvider,
                        expressionEvaluationContext,
                        cancellationContext,
                        ioStreamingOptions,
                        runOptionsWithProxyEnvironmentVariablePropagationDisabled,
                        systemInfo
                    )
                }

                beforeEachTest {
                    whenever(imagesClient.build(eq(buildDirectory), any(), eq(dockerfilePath), eq(setOf(imageTag)), eq(outputSink), eq(cancellationContext), any())).thenReturn(image)

                    runnerWithProxyEnvironmentVariablePropagationDisabled.run(step, eventSink)
                }

                it("does not pass the proxy-related environment variables as image build arguments, but does still pass the user-provided build args with any environment variable references resolved") {
                    val expectedArgs = mapOf(
                        "some_arg" to "some_value",
                        "SOME_PROXY_CONFIG" to "overridden",
                        "SOME_HOST_VAR" to "some env var value"
                    )

                    verify(imagesClient).build(any(), eq(expectedArgs), any(), any(), any(), any(), any())
                }

                it("emits a 'image built' event") {
                    verify(eventSink).postEvent(ImageBuiltEvent(container, image))
                }
            }
        }

        on("when building the image fails") {
            beforeEachTest {
                whenever(imagesClient.build(eq(buildDirectory), any(), eq(dockerfilePath), eq(setOf(imageTag)), eq(outputSink), eq(cancellationContext), any())).thenThrow(ImageBuildFailedException("Something went wrong.\nMore details on this line."))
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

            val imageSourceWithInvalidBuildArgReference = BuildImage(buildDirectory, mapOf("SOME_HOST_VAR" to invalidReference), dockerfilePath)
            val containerWithInvalidBuildArgReference = Container("some-container", imageSourceWithInvalidBuildArgReference)

            val stepWithInvalidBuildArgReference = BuildImageStep(containerWithInvalidBuildArgReference, imageTag)

            beforeEachTest {
                runner.run(stepWithInvalidBuildArgReference, eventSink)
            }

            it("emits a 'image build failed' event") {
                verify(eventSink).postEvent(ImageBuildFailedEvent(containerWithInvalidBuildArgReference, "The value for the build arg 'SOME_HOST_VAR' cannot be evaluated: Couldn't evaluate expression."))
            }
        }
    }
})
