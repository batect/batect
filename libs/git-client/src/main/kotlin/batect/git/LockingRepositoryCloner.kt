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

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

@OptIn(ExperimentalTime::class)
class LockingRepositoryCloner(
    private val client: GitClient,
    private val cloneTimeout: Duration = 5.minutes,
) {
    fun clone(repo: String, ref: String, destination: Path) {
        val lockFile = destination.resolveSibling("${destination.name}.lock")
        val startTime = TimeSource.Monotonic.markNow()

        while (true) {
            if (tryToAcquireLock(lockFile) == LockState.Acquired) {
                break
            }

            if (startTime.elapsedNow() > cloneTimeout) {
                throw GitException("Could not clone repository '$repo' into '$destination': timed out after $cloneTimeout, another process may be using '$lockFile'")
            }

            Thread.sleep(100)
        }

        try {
            if (Files.exists(destination)) {
                return
            }

            client.clone(repo, ref, destination)
        } finally {
            releaseLock(lockFile)
        }
    }

    private fun tryToAcquireLock(lockFile: Path): LockState {
        return try {
            Files.createFile(lockFile)
            LockState.Acquired
        } catch (e: FileAlreadyExistsException) {
            LockState.NotAcquired
        }
    }

    private fun releaseLock(lockFile: Path) {
        Files.delete(lockFile)
    }

    private enum class LockState {
        Acquired,
        NotAcquired,
    }
}
