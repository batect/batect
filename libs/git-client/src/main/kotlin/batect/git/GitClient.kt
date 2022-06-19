/*
    Copyright 2017-2022 Charles Korn.

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
import batect.os.ProcessRunner
import java.nio.file.Path

class GitClient(
    private val processRunner: ProcessRunner
) {
    fun clone(repo: String, ref: String, destination: Path) {
        try {
            val cloneExitCode = processRunner.runWithConsoleAttached(listOf("git", "clone", "--quiet", "--no-checkout", "--", repo, destination.toString()))

            if (cloneExitCode != 0) {
                throw GitException("Could not clone repository '$repo': Git command exited with code $cloneExitCode.")
            }

            val checkoutResult = processRunner.runAndCaptureOutput(listOf("git", "-c", "advice.detachedHead=false", "-C", destination.toString(), "checkout", "--quiet", "--recurse-submodules", ref))

            if (checkoutResult.exitCode != 0) {
                throw GitException("Could not check out reference '$ref' for repository '$repo': Git command exited with code ${checkoutResult.exitCode}: ${checkoutResult.output.trim()}")
            }
        } catch (e: ExecutableDoesNotExistException) {
            throw GitException("Could not clone repository: ${e.message}", e)
        }
    }

    val version: GitVersionRetrievalResult by lazy {
        try {
            val command = listOf("git", "--version")
            val result = processRunner.runAndCaptureOutput(command)

            when (result.exitCode) {
                0 -> GitVersionRetrievalResult.Succeeded(result.output.trim().removePrefix("git version "))
                else -> GitVersionRetrievalResult.Failed("'${command.joinToString(" ")}' exited with code ${result.exitCode}: ${result.output.trim()}")
            }
        } catch (e: ExecutableDoesNotExistException) {
            GitVersionRetrievalResult.Failed(e.message!!)
        }
    }
}
