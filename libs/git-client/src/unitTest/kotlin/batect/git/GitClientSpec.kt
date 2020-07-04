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

package batect.git

import batect.os.ExecutableDoesNotExistException
import batect.os.ProcessOutput
import batect.os.ProcessRunner
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.osIndependentPath
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object GitClientSpec : Spek({
    describe("a Git client") {
        val processRunner by createForEachTest { mock<ProcessRunner>() }
        val client by createForEachTest { GitClient(processRunner) }

        describe("cloning a repository") {
            val repo = "http://github.com/me/my-repo.git"
            val ref = "the-reference"
            val directory = osIndependentPath("/some/directory")
            val cloneCommand = listOf("git", "clone", "--quiet", "--no-checkout", "--", repo, "/some/directory")

            given("cloning the repository succeeds") {
                beforeEachTest {
                    whenever(processRunner.runWithConsoleAttached(cloneCommand)).doReturn(0)
                }

                val checkoutCommand = listOf("git", "-c", "advice.detachedHead=false", "-C", "/some/directory", "checkout", "--quiet", "--recurse-submodules", ref)

                given("checking out the reference succeeds") {
                    beforeEachTest {
                        whenever(processRunner.runAndCaptureOutput(checkoutCommand)).doReturn(ProcessOutput(0, ""))

                        client.clone(repo, ref, directory)
                    }

                    it("clones the repo before checking out the provided reference") {
                        inOrder(processRunner) {
                            verify(processRunner).runWithConsoleAttached(cloneCommand)
                            verify(processRunner).runAndCaptureOutput(checkoutCommand)
                        }
                    }
                }

                given("checking out the reference fails") {
                    beforeEachTest {
                        whenever(processRunner.runAndCaptureOutput(checkoutCommand)).doReturn(ProcessOutput(1, "Something went wrong.\n"))
                    }

                    it("throws an appropriate exception") {
                        assertThat({ client.clone(repo, ref, directory) }, throws<GitException>(withMessage("Could not check out reference 'the-reference' for repository 'http://github.com/me/my-repo.git': Git command exited with code 1: Something went wrong.")))
                    }
                }
            }

            given("cloning the repository fails") {
                beforeEachTest {
                    whenever(processRunner.runWithConsoleAttached(cloneCommand)).doReturn(2)
                }

                it("throws an appropriate exception") {
                    assertThat({ client.clone(repo, ref, directory) }, throws<GitException>(withMessage("Could not clone repository 'http://github.com/me/my-repo.git': Git command exited with code 2.")))
                }
            }

            given("the Git client is not available") {
                beforeEachTest {
                    whenever(processRunner.runWithConsoleAttached(cloneCommand)).doThrow(ExecutableDoesNotExistException("git", RuntimeException("Something went wrong")))
                }

                it("throws an appropriate exception") {
                    assertThat({ client.clone(repo, ref, directory) }, throws<GitException>(withMessage("Could not clone repository: The executable 'git' could not be found or is not executable.")))
                }
            }
        }

        describe("getting the Git version") {
            val command = listOf("git", "--version")

            given("the Git client is available") {
                given("running the version command succeeds") {
                    beforeEachTest {
                        whenever(processRunner.runAndCaptureOutput(command)).doReturn(ProcessOutput(0, "git version 1.2.3\n"))
                    }

                    val result by runForEachTest { client.getVersion() }

                    it("returns the version returned by Git") {
                        assertThat(result, equalTo(GitVersionRetrievalResult.Succeeded("1.2.3")))
                    }
                }

                given("running the version command fails") {
                    beforeEachTest {
                        whenever(processRunner.runAndCaptureOutput(command)).doReturn(ProcessOutput(1, "I can't do that\n"))
                    }

                    val result by runForEachTest { client.getVersion() }

                    it("returns the version returned by Git") {
                        assertThat(result, equalTo(GitVersionRetrievalResult.Failed("'git --version' exited with code 1: I can't do that")))
                    }
                }
            }

            given("the Git client is not available") {
                beforeEachTest {
                    whenever(processRunner.runAndCaptureOutput(command))
                        .doThrow(ExecutableDoesNotExistException("git", RuntimeException("Something went wrong")))
                }

                val result by runForEachTest { client.getVersion() }

                it("returns an error message explaining the issue") {
                    assertThat(result, equalTo(GitVersionRetrievalResult.Failed("The executable 'git' could not be found or is not executable.")))
                }
            }
        }
    }
})
