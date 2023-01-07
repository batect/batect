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

package batect.git

import batect.logging.Logger
import batect.logging.NullLogSink
import batect.os.ProcessRunner
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files
import kotlin.time.Duration.Companion.seconds

object LockingRepositoryClonerIntegrationTest : Spek({
    describe("a locking Git repository cloner") {
        val logger by createForEachTest { Logger("Git client", NullLogSink()) }
        val processRunner by createForEachTest { ProcessRunner(logger) }
        val gitClient by createForEachTest { GitClient(processRunner) }

        val tempDirectory by createForEachTest { Files.createTempDirectory("batect-locking-repository-cloner-integration-test") }
        afterEachTest { tempDirectory.toFile().deleteRecursively() }

        val targetDirectory by createForEachTest { tempDirectory.resolve("target") }
        val lockFile by createForEachTest { tempDirectory.resolve("target.lock") }

        val testRepo = "https://github.com/batect/test-git-repo.git"
        val testReference = "da73710ec475b32ac2e623d8c69f10c3f0c71a7f"
        val testFile by createForEachTest { targetDirectory.resolve("the-file") }
        val expectedFileContent = "This is the first version"

        fun cloneTestRepo() {
            val cloner = LockingRepositoryCloner(gitClient)
            cloner.clone(testRepo, testReference, targetDirectory)
            assertThat(String(Files.readAllBytes(testFile)).trim(), equalTo(expectedFileContent))
        }

        it("should successfully clone while there are no competing processes attempting to clone to the same location") {
            cloneTestRepo()
        }

        it("should throw an appropriate exception if the target directory is locked until the timeout expires") {
            Files.createFile(lockFile)

            val cloner = LockingRepositoryCloner(gitClient, 1.seconds)

            assertThat(
                { cloner.clone(testRepo, testReference, targetDirectory) },
                throws<GitException>(withMessage("Could not clone repository '$testRepo' into '$targetDirectory': timed out after 1s, another process may be using '$lockFile'")),
            )
        }

        it("should successfully clone while many competing processes attempt to clone to the same location") {
            runBlocking {
                val concurrency = 100
                val rendezvousPoint = Semaphore(concurrency, concurrency)

                repeat(concurrency) {
                    launch(Dispatchers.IO) {
                        rendezvousPoint.acquire()
                        cloneTestRepo()
                    }
                }

                repeat(concurrency) {
                    rendezvousPoint.release()
                }
            }
        }
    }
})
