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

package batect.config.includes

import batect.testutils.createForEachTest
import batect.testutils.given
import batect.ui.Console
import batect.ui.OutputStyle
import batect.ui.text.Text
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
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

                it("prints a blank line to the console to separate any output from Git from any further output from batect") {
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
                    verifyZeroInteractions(console)
                }
            }

            describe("when a repository has been cloned") {
                beforeEachTest { listener.onCloneComplete() }

                it("prints a blank line to the console to separate any output from Git from any further output from batect") {
                    verifyZeroInteractions(console)
                }
            }
        }
    }
})
