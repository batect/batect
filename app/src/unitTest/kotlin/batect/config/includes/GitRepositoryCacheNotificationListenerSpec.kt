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

package batect.config.includes

import batect.testutils.createForEachTest
import batect.testutils.given
import batect.ui.Console
import batect.ui.OutputStyle
import batect.ui.text.Text
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object GitRepositoryCacheNotificationListenerSpec : Spek({
    describe("a Git repository cache notification listener") {
        val console by createForEachTest { mock<Console>() }

        given("quiet output is not being used") {
            val outputStyle = OutputStyle.Simple
            val listener by createForEachTest { DefaultGitRepositoryCacheNotificationListener(console, outputStyle) }

            describe("when a repository is being cloned") {
                val repo = GitRepositoryReference("https://myrepo.com/bundles/bundle.git", "v1.2.3")

                beforeEachTest { listener.onCloning(repo) }

                it("prints a message to the console") {
                    verify(console).println(Text.white(Text("Cloning ") + Text.bold("https://myrepo.com/bundles/bundle.git") + Text(" ") + Text.bold("v1.2.3") + Text("...")))
                }
            }

            describe("when a repository has been cloned") {
                beforeEachTest { listener.onCloneComplete() }

                it("prints a blank line to the console to separate any output from Git from any further output from Batect") {
                    verify(console).println()
                }
            }
        }

        given("quiet output is being used") {
            val outputStyle = OutputStyle.Quiet
            val listener by createForEachTest { DefaultGitRepositoryCacheNotificationListener(console, outputStyle) }

            describe("when a repository is being cloned") {
                val repo = GitRepositoryReference("https://myrepo.com/bundles/bundle.git", "v1.2.3")

                beforeEachTest { listener.onCloning(repo) }

                it("does not print anything to the console") {
                    verifyNoInteractions(console)
                }
            }

            describe("when a repository has been cloned") {
                beforeEachTest { listener.onCloneComplete() }

                it("prints a blank line to the console to separate any output from Git from any further output from Batect") {
                    verifyNoInteractions(console)
                }
            }
        }
    }
})
