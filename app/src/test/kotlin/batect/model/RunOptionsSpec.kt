/*
   Copyright 2017 Charles Korn.

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

package batect.model

import batect.cli.CommandLineOptions
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object RunOptionsSpec : Spek({
    describe("a set of run options") {
        given("a set of command line options with cleanup after failure enabled") {
            val commandLineOptions = CommandLineOptions(
                disableCleanupAfterFailure = false,
                levelOfParallelism = 444,
                dontPropagateProxyEnvironmentVariables = true
            )

            on("creating a set of run options") {
                val runOptions = RunOptions(commandLineOptions)

                it("takes the level of parallelism from the command line options") {
                    assertThat(runOptions.levelOfParallelism, equalTo(commandLineOptions.levelOfParallelism))
                }

                it("enables cleanup after failure") {
                    assertThat(runOptions.behaviourAfterFailure, equalTo(BehaviourAfterFailure.Cleanup))
                }

                it("takes the proxy environment variable behaviour from the command line options") {
                    assertThat(runOptions.propagateProxyEnvironmentVariables, equalTo(false))
                }
            }
        }

        given("a set of command line options with cleanup after failure disabled") {
            val commandLineOptions = CommandLineOptions(
                disableCleanupAfterFailure = true,
                levelOfParallelism = 444,
                dontPropagateProxyEnvironmentVariables = false
            )

            on("creating a set of run options") {
                val runOptions = RunOptions(commandLineOptions)

                it("takes the level of parallelism from the command line options") {
                    assertThat(runOptions.levelOfParallelism, equalTo(commandLineOptions.levelOfParallelism))
                }

                it("enables cleanup after failure") {
                    assertThat(runOptions.behaviourAfterFailure, equalTo(BehaviourAfterFailure.DontCleanup))
                }

                it("takes the proxy environment variable behaviour from the command line options") {
                    assertThat(runOptions.propagateProxyEnvironmentVariables, equalTo(true))
                }
            }
        }
    }
})
