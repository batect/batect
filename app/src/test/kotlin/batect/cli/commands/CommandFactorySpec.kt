/*
   Copyright 2017-2018 Charles Korn.

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

package batect.cli.commands

import batect.cli.CommandLineOptions
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isA
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object CommandFactorySpec : Spek({
    describe("a command factory") {
        val factory = CommandFactory()
        val kodein = Kodein {
            bind<HelpCommand>() with instance(mock())
            bind<VersionInfoCommand>() with instance(mock())
            bind<ListTasksCommand>() with instance(mock())
            bind<RunTaskCommand>() with instance(mock())
        }

        given("a set of options with the 'show help' flag set") {
            val options = CommandLineOptions(showHelp = true)
            val command = factory.createCommand(options, kodein)

            on("creating the command") {
                it("returns a help command") {
                    assertThat(command, isA<HelpCommand>())
                }
            }
        }

        given("a set of options with the 'show version info' flag set") {
            val options = CommandLineOptions(showVersionInfo = true)
            val command = factory.createCommand(options, kodein)

            on("creating the command") {
                it("returns a version info command") {
                    assertThat(command, isA<VersionInfoCommand>())
                }
            }
        }

        given("a set of options with the 'list tasks' flag set") {
            val options = CommandLineOptions(listTasks = true)
            val command = factory.createCommand(options, kodein)

            on("creating the command") {
                it("returns a list tasks command") {
                    assertThat(command, isA<ListTasksCommand>())
                }
            }
        }

        given("a set of options with no special flags set") {
            val options = CommandLineOptions()
            val command = factory.createCommand(options, kodein)

            on("creating the command") {
                it("returns a run task command") {
                    assertThat(command, isA<RunTaskCommand>())
                }
            }
        }
    }
})
