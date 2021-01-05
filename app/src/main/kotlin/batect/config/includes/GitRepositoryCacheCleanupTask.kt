/*
   Copyright 2017-2021 Charles Korn.

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

import batect.logging.LogMessageBuilder
import batect.logging.Logger
import batect.telemetry.TelemetrySessionBuilder
import batect.telemetry.addUnhandledExceptionEvent
import java.time.ZonedDateTime
import kotlin.concurrent.thread

class GitRepositoryCacheCleanupTask(
    private val cache: GitRepositoryCache,
    private val telemetrySessionBuilder: TelemetrySessionBuilder,
    private val logger: Logger,
    private val threadRunner: ThreadRunner = defaultThreadRunner,
    private val timeSource: TimeSource = ZonedDateTime::now
) {
    fun start() {
        threadRunner(::runOnThread)
    }

    private fun runOnThread() {
        val now = timeSource()
        val threshold = now.minusDays(30)
        val repos = cache.listAll().filter { it.lastUsed < threshold }

        if (repos.isEmpty()) {
            logger.info {
                message("No repositories ready for clean up.")
            }

            return
        }

        repos.forEach(::delete)
    }

    private fun delete(repo: CachedGitRepository) {
        logger.info {
            message("Deleting repository as it has not been used in over 30 days.")
            data("repo", repo.repo)
            data("lastUsed", repo.lastUsed)
        }

        try {
            cache.delete(repo)

            logger.info {
                message("Repository deletion completed.")
                data("repo", repo.repo)
            }
        } catch (e: Throwable) {
            logger.warn {
                message("Repository deletion failed.")
                data("repo", repo.repo)
                exception(e)
            }

            telemetrySessionBuilder.addUnhandledExceptionEvent(e, isUserFacing = false)
        }
    }

    companion object {
        private val defaultThreadRunner: ThreadRunner = { block -> thread(isDaemon = true, name = GitRepositoryCacheCleanupTask::class.qualifiedName, block = block) }
    }
}

private fun LogMessageBuilder.data(key: String, value: GitRepositoryReference) = data(key, value, GitRepositoryReference.serializer())

typealias ThreadRunner = (BackgroundProcess) -> Unit
typealias BackgroundProcess = () -> Unit
