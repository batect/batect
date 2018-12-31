/*
   Copyright 2017-2019 Charles Korn.

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

package batect.os.proxies

import batect.docker.DockerHostNameResolutionResult
import batect.docker.DockerHostNameResolver
import batect.logging.Logger
import batect.logging.Severity
import batect.testutils.InMemoryLogSink
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.hasMessage
import batect.testutils.withAdditionalData
import batect.testutils.withLogMessage
import batect.testutils.withSeverity
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ProxyEnvironmentVariablePreprocessorSpec : Spek({
    describe("a proxy environment variable preprocessor") {
        val hostNameResolver by createForEachTest { mock<DockerHostNameResolver>() }
        val logSink by createForEachTest { InMemoryLogSink() }
        val logger by createForEachTest { Logger("ProxyEnvironmentVariablePreprocessor", logSink) }
        val preprocessor by createForEachTest { ProxyEnvironmentVariablePreprocessor(hostNameResolver, logger) }

        given("getting the local hostname is not supported") {
            beforeEachTest { whenever(hostNameResolver.resolveNameOfDockerHost()).doReturn(DockerHostNameResolutionResult.NotSupported) }

            on("processing a variable that does not refer to the local machine") {
                val result = preprocessor.process("http://proxy.mycompany.com")

                it("returns the original value unchanged") {
                    assertThat(result, equalTo("http://proxy.mycompany.com"))
                }

                it("logs that the variable was not processed") {
                    assertThat(logSink, hasMessage(
                        withSeverity(Severity.Info) and
                            withLogMessage("Not attempting to preprocess proxy environment variable value because getting the local Docker hostname is not supported.") and
                            withAdditionalData("value", "http://proxy.mycompany.com")
                    ))
                }
            }

            on("processing a variable that refers to the local machine") {
                val result = preprocessor.process("http://localhost")

                it("returns the original value unchanged") {
                    assertThat(result, equalTo("http://localhost"))
                }

                it("logs that the variable was not processed") {
                    assertThat(logSink, hasMessage(
                        withSeverity(Severity.Info) and
                            withLogMessage("Not attempting to preprocess proxy environment variable value because getting the local Docker hostname is not supported.") and
                            withAdditionalData("value", "http://localhost")
                    ))
                }
            }
        }

        given("getting the local hostname succeeds") {
            beforeEachTest { whenever(hostNameResolver.resolveNameOfDockerHost()).doReturn(DockerHostNameResolutionResult.Resolved("local.docker")) }

            on("processing a variable that does not refer to the local machine") {
                val result = preprocessor.process("http://proxy.mycompany.com")

                it("returns the original value unchanged") {
                    assertThat(result, equalTo("http://proxy.mycompany.com"))
                }

                it("logs that the variable was not processed") {
                    assertThat(logSink, hasMessage(
                        withSeverity(Severity.Info) and
                            withLogMessage("Not preprocessing proxy environment variable value because it does not refer to the local machine.") and
                            withAdditionalData("value", "http://proxy.mycompany.com")
                    ))
                }
            }

            listOf(
                "localhost",
                "127.0.0.1",
                "[::1]" // See https://serverfault.com/questions/205793/how-can-one-distinguish-the-host-and-the-port-in-an-ipv6-url for an explanation of why the square brackets are required.
            ).forEach { localhostAddress ->
                mapOf(
                    "http://$localhostAddress" to "http://local.docker/",
                    "http://$localhostAddress/" to "http://local.docker/",
                    "https://$localhostAddress" to "https://local.docker/",
                    "http://$localhostAddress:1234" to "http://local.docker:1234/",
                    "http://$localhostAddress/thing" to "http://local.docker/thing",
                    "http://user@$localhostAddress" to "http://user@local.docker/",
                    "http://user:password@$localhostAddress" to "http://user:password@local.docker/"
                ).forEach { originalValue, expectedValue ->
                    on("processing a variable that refers to the local machine through '$originalValue'") {
                        val result = preprocessor.process(originalValue)

                        it("returns the original value with the hostname replaced by the hostname of the Docker host") {
                            assertThat(result, equalTo(expectedValue))
                        }

                        it("logs that the variable was processed") {
                            assertThat(logSink, hasMessage(
                                withSeverity(Severity.Info) and
                                    withLogMessage("Modified proxy environment variable that referred to the local machine.") and
                                    withAdditionalData("originalValue", originalValue) and
                                    withAdditionalData("newValue", expectedValue)
                            ))
                        }
                    }
                }
            }

            listOf(
                "thing:1234",
                "localhost",
                "127.0.0.1",
                "::1",
                "[::1]",
                "http://::1"
            ).forEach { address ->
                on("processing a variable that is in the invalid format '$address'") {
                    val result = preprocessor.process(address)

                    it("returns the original value unchanged") {
                        assertThat(result, equalTo(address))
                    }

                    it("logs that the variable was not processed") {
                        assertThat(logSink, hasMessage(
                            withSeverity(Severity.Warning) and
                                withLogMessage("Not attempting to preprocess proxy environment variable value because it is not a valid URL.") and
                                withAdditionalData("value", address)
                        ))
                    }
                }
            }
        }
    }
})
