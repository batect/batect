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

package batect.telemetry

import batect.cli.CommandLineOptions
import batect.cli.commands.completion.Shell
import batect.os.ConsoleInfo
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.ui.Console
import batect.ui.OutputStyle
import batect.ui.Prompt
import batect.ui.YesNoAnswer
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

object TelemetryConsentPromptSpec : Spek({
    describe("a telemetry consent prompt") {
        val configurationStore by createForEachTest { mock<TelemetryConfigurationStore>() }
        val commandLineOptions by createForEachTest { mock<CommandLineOptions>() }
        val consoleInfo by createForEachTest { mock<ConsoleInfo>() }
        val ciEnvironmentDetector by createForEachTest { mock<CIEnvironmentDetector>() }
        val console by createForEachTest { mock<Console>() }
        val prompt by createForEachTest { mock<Prompt>() }
        val consentPrompt by createForEachTest { TelemetryConsentPrompt(configurationStore, commandLineOptions, consoleInfo, ciEnvironmentDetector, console, prompt) }

        val userId by createForEachTest { UUID.randomUUID() }

        describe("asking for consent if required") {
            fun Suite.itDoesNotPromptForConsent() {
                beforeEachTest {
                    consentPrompt.askForConsentIfRequired()
                }

                it("does not print anything to the console") {
                    verifyNoInteractions(console)
                }

                it("does not prompt the user") {
                    verifyNoInteractions(prompt)
                }

                it("does not change the stored telemetry configuration") {
                    verify(configurationStore, never()).saveConfiguration(any())
                }
            }

            fun Suite.itShowsTheNonInteractiveConsentPrompt() {
                beforeEachTest {
                    consentPrompt.askForConsentIfRequired()
                }

                it("does not prompt the user to enter a response") {
                    verifyNoInteractions(prompt)
                }

                it("does not change the stored telemetry configuration") {
                    verify(configurationStore, never()).saveConfiguration(any())
                }

                it("prints a message asking the user to make a decision and configure Batect appropriately") {
                    inOrder(console) {
                        verify(console).println("Batect can collect anonymous environment, usage and performance information.")
                        verify(console).println("This information does not include personal or sensitive information, and is used only to help improve Batect.")
                        verify(console).println("More information is available at https://batect.dev/privacy, including details of what information is collected and a formal privacy policy.")
                        verify(console).println()
                        verify(console).println("It looks like Batect is running in a non-interactive session, so it can't ask for permission to collect and report this information. To suppress this message:")
                        verify(console).println("* To allow collection of data, set the BATECT_ENABLE_TELEMETRY environment variable to 'true', or run './batect --permanently-enable-telemetry'.")
                        verify(console).println("* To prevent collection of data, set the BATECT_ENABLE_TELEMETRY environment variable to 'false', or run './batect --permanently-disable-telemetry'.")
                        verify(console).println()
                        verify(console).println("No data will be collected for this session.")
                        verify(console).println()
                    }
                }
            }

            fun Suite.itPromptsTheUserForTheirChoiceAndStoresIt(expectedConsentState: ConsentState) {
                beforeEachTest { consentPrompt.askForConsentIfRequired() }

                it("prints a message asking the user to make a decision and prompts them for their answer") {
                    inOrder(console, prompt) {
                        verify(console).println("Batect can collect anonymous environment, usage and performance information.")
                        verify(console).println("This information does not include personal or sensitive information, and is used only to help improve Batect.")
                        verify(console).println("More information is available at https://batect.dev/privacy, including details of what information is collected and a formal privacy policy.")
                        verify(console).println()
                        verify(prompt).askYesNoQuestion("Is it OK for Batect to collect this information?")
                        verify(console).println()
                    }
                }

                it("stores the user's choice, preserving their existing user ID") {
                    verify(configurationStore).saveConfiguration(TelemetryConfiguration(userId, expectedConsentState))
                }
            }

            given("the user has already consented to telemetry") {
                beforeEachTest {
                    whenever(configurationStore.currentConfiguration).doReturn(TelemetryConfiguration(UUID.randomUUID(), ConsentState.TelemetryAllowed))
                }

                itDoesNotPromptForConsent()
            }

            given("the user has already declined telemetry") {
                beforeEachTest {
                    whenever(configurationStore.currentConfiguration).doReturn(TelemetryConfiguration(UUID.randomUUID(), ConsentState.TelemetryDisabled))
                }

                itDoesNotPromptForConsent()
            }

            given("the user has not already consented to or declined telemetry") {
                beforeEachTest {
                    whenever(configurationStore.currentConfiguration).doReturn(TelemetryConfiguration(userId, ConsentState.None))
                }

                given("the user has disabled telemetry on the command line") {
                    beforeEachTest {
                        whenever(commandLineOptions.disableTelemetry).doReturn(true)
                    }

                    itDoesNotPromptForConsent()
                }

                given("the user has enabled telemetry on the command line") {
                    beforeEachTest {
                        whenever(commandLineOptions.disableTelemetry).doReturn(false)
                    }

                    itDoesNotPromptForConsent()
                }

                given("the user has not disabled or enabled telemetry on the command line") {
                    beforeEachTest {
                        whenever(commandLineOptions.disableTelemetry).doReturn(null)
                    }

                    given("the user is permanently enabling telemetry via the command line") {
                        beforeEachTest {
                            whenever(commandLineOptions.permanentlyEnableTelemetry).doReturn(true)
                        }

                        itDoesNotPromptForConsent()
                    }

                    given("the user is permanently disabling telemetry via the command line") {
                        beforeEachTest {
                            whenever(commandLineOptions.permanentlyDisableTelemetry).doReturn(true)
                        }

                        itDoesNotPromptForConsent()
                    }

                    given("the user is retrieving a shell tab completion script") {
                        beforeEachTest {
                            whenever(commandLineOptions.generateShellTabCompletionScript).doReturn(Shell.Fish)
                        }

                        itDoesNotPromptForConsent()
                    }

                    given("the user is retrieving task information for shell tab completion") {
                        beforeEachTest {
                            whenever(commandLineOptions.generateShellTabCompletionTaskInformation).doReturn(Shell.Fish)
                        }

                        itDoesNotPromptForConsent()
                    }

                    given("the user is not permanently enabling or disabling telemetry via the command line") {
                        beforeEachTest {
                            whenever(commandLineOptions.permanentlyDisableTelemetry).doReturn(false)
                            whenever(commandLineOptions.permanentlyEnableTelemetry).doReturn(false)
                        }

                        given("the user has requested the quiet output style") {
                            beforeEachTest {
                                whenever(commandLineOptions.requestedOutputStyle).doReturn(OutputStyle.Quiet)
                            }

                            itDoesNotPromptForConsent()
                        }

                        given("the user has not requested the quiet output style") {
                            beforeEachTest {
                                whenever(commandLineOptions.requestedOutputStyle).doReturn(OutputStyle.Fancy)
                            }

                            given("stdin is a TTY") {
                                beforeEachTest {
                                    whenever(consoleInfo.stdinIsTTY).thenReturn(true)
                                }

                                given("the application is not running on CI") {
                                    beforeEachTest {
                                        whenever(ciEnvironmentDetector.detect()).doReturn(CIDetectionResult(false, null))
                                    }

                                    given("the user answers 'yes' to the prompt") {
                                        beforeEachTest { whenever(prompt.askYesNoQuestion(any())).thenReturn(YesNoAnswer.Yes) }

                                        itPromptsTheUserForTheirChoiceAndStoresIt(ConsentState.TelemetryAllowed)
                                    }

                                    given("the user answers 'no' to the prompt") {
                                        beforeEachTest { whenever(prompt.askYesNoQuestion(any())).thenReturn(YesNoAnswer.No) }

                                        itPromptsTheUserForTheirChoiceAndStoresIt(ConsentState.TelemetryDisabled)
                                    }
                                }

                                given("the application is running on CI") {
                                    beforeEachTest {
                                        whenever(ciEnvironmentDetector.detect()).doReturn(CIDetectionResult(true, null))
                                    }

                                    itShowsTheNonInteractiveConsentPrompt()
                                }
                            }

                            given("stdin is not a TTY") {
                                beforeEachTest {
                                    whenever(consoleInfo.stdinIsTTY).thenReturn(false)
                                }

                                itShowsTheNonInteractiveConsentPrompt()
                            }
                        }
                    }
                }
            }
        }
    }
})
