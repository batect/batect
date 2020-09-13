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

import batect.cli.CommandLineOptions
import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RunOptionsSpec : Spek({
    describe("a set of run options") {
        data class TestCase(
            val isMainTask: Boolean,
            val disableCleanupAfterSuccessOnCommandLine: Boolean,
            val disableCleanupAfterFailureOnCommandLine: Boolean,
            val expectedBehaviourAfterSuccess: CleanupOption,
            val expectedBehaviourAfterFailure: CleanupOption
        ) {
            val description =
                "the task ${if (isMainTask) "is" else "isn't"} the main task, cleanup after success ${if (disableCleanupAfterSuccessOnCommandLine) "is" else "isn't"} disabled and cleanup after failure ${if (disableCleanupAfterFailureOnCommandLine) "is" else "isn't"} disabled"
        }

        setOf(
            TestCase(
                isMainTask = false,
                disableCleanupAfterSuccessOnCommandLine = false,
                disableCleanupAfterFailureOnCommandLine = false,
                expectedBehaviourAfterSuccess = CleanupOption.Cleanup,
                expectedBehaviourAfterFailure = CleanupOption.Cleanup
            ),
            TestCase(
                isMainTask = false,
                disableCleanupAfterSuccessOnCommandLine = false,
                disableCleanupAfterFailureOnCommandLine = true,
                expectedBehaviourAfterSuccess = CleanupOption.Cleanup,
                expectedBehaviourAfterFailure = CleanupOption.DontCleanup
            ),
            TestCase(
                isMainTask = false,
                disableCleanupAfterSuccessOnCommandLine = true,
                disableCleanupAfterFailureOnCommandLine = false,
                expectedBehaviourAfterSuccess = CleanupOption.Cleanup,
                expectedBehaviourAfterFailure = CleanupOption.Cleanup
            ),
            TestCase(
                isMainTask = false,
                disableCleanupAfterSuccessOnCommandLine = true,
                disableCleanupAfterFailureOnCommandLine = true,
                expectedBehaviourAfterSuccess = CleanupOption.Cleanup,
                expectedBehaviourAfterFailure = CleanupOption.DontCleanup
            ),
            TestCase(
                isMainTask = true,
                disableCleanupAfterSuccessOnCommandLine = false,
                disableCleanupAfterFailureOnCommandLine = false,
                expectedBehaviourAfterSuccess = CleanupOption.Cleanup,
                expectedBehaviourAfterFailure = CleanupOption.Cleanup
            ),
            TestCase(
                isMainTask = true,
                disableCleanupAfterSuccessOnCommandLine = false,
                disableCleanupAfterFailureOnCommandLine = true,
                expectedBehaviourAfterSuccess = CleanupOption.Cleanup,
                expectedBehaviourAfterFailure = CleanupOption.DontCleanup
            ),
            TestCase(
                isMainTask = true,
                disableCleanupAfterSuccessOnCommandLine = true,
                disableCleanupAfterFailureOnCommandLine = false,
                expectedBehaviourAfterSuccess = CleanupOption.DontCleanup,
                expectedBehaviourAfterFailure = CleanupOption.Cleanup
            ),
            TestCase(
                isMainTask = true,
                disableCleanupAfterSuccessOnCommandLine = true,
                disableCleanupAfterFailureOnCommandLine = true,
                expectedBehaviourAfterSuccess = CleanupOption.DontCleanup,
                expectedBehaviourAfterFailure = CleanupOption.DontCleanup
            )
        ).forEach { testCase ->
            given(testCase.description) {
                val commandLineOptions = CommandLineOptions(disableCleanupAfterSuccess = testCase.disableCleanupAfterSuccessOnCommandLine, disableCleanupAfterFailure = testCase.disableCleanupAfterFailureOnCommandLine)
                val runOptions = RunOptions(testCase.isMainTask, commandLineOptions)

                it("reports the expected behaviour after success") {
                    assertThat(runOptions.behaviourAfterSuccess, equalTo(testCase.expectedBehaviourAfterSuccess))
                }

                it("reports the expected behaviour after failure") {
                    assertThat(runOptions.behaviourAfterFailure, equalTo(testCase.expectedBehaviourAfterFailure))
                }
            }
        }
    }
})
