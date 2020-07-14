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

import batect.logging.Logger
import batect.logging.NullLogSink
import batect.os.ProcessRunner
import batect.os.deleteDirectory
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.matches
import java.nio.file.Files
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object GitClientIntegrationTest : Spek({
    describe("a Git client") {
        val logger by createForEachTest { Logger("Git client", NullLogSink()) }
        val processRunner by createForEachTest { ProcessRunner(logger) }
        val client by createForEachTest { GitClient(processRunner) }

        describe("cloning a repository") {
            data class Scenario(val description: String, val reference: String, val expectedFileContent: String)

            setOf(
                Scenario("a short commit hash", "da73710", "This is the first version"),
                Scenario("a full commit hash", "da73710ec475b32ac2e623d8c69f10c3f0c71a7f", "This is the first version"),
                Scenario("a branch", "the-branch", "This is the file on the branch"),
                Scenario("a tag", "the-tag", "This is the first version")
            ).forEach { scenario ->
                describe("cloning ${scenario.description}") {
                    val targetDirectory by createForEachTest { Files.createTempDirectory("batect-git-integration-test") }

                    beforeEachTest {
                        client.clone("https://github.com/batect/test-git-repo.git", scenario.reference, targetDirectory)
                    }

                    afterEachTest {
                        deleteDirectory(targetDirectory)
                    }

                    it("clones the repository at the expected commit") {
                        assertThat(String(Files.readAllBytes(targetDirectory.resolve("the-file"))).trim(), equalTo(scenario.expectedFileContent))
                    }
                }
            }
        }

        describe("getting the Git client version") {
            val result by runForEachTest { client.getVersion() }

            it("returns a successful result in the expected format") {
                assertThat((result as GitVersionRetrievalResult.Succeeded).version, matches("""^\d+\.\d+\.\d+.*$""".toRegex()))
            }
        }
    }
})
