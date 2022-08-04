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

package batect.updates

import batect.logging.Logger
import batect.telemetry.TelemetryCaptor
import batect.telemetry.addUnhandledExceptionEvent
import kotlin.concurrent.thread

class UpdateInfoUpdater(
    private val updateInfoDownloader: UpdateInfoDownloader,
    private val updateInfoStorage: UpdateInfoStorage,
    private val telemetryCaptor: TelemetryCaptor,
    private val logger: Logger,
    private val threadRunner: ThreadRunner = defaultThreadRunner
) {
    fun updateCachedInfo() {
        threadRunner {
            try {
                val updateInfo = updateInfoDownloader.getLatestVersionInfo()
                updateInfoStorage.write(updateInfo)
            } catch (e: Throwable) {
                logger.warn {
                    message("Could not update cached update information.")
                    exception(e)
                }

                telemetryCaptor.addUnhandledExceptionEvent(e, isUserFacing = false)
            }
        }
    }

    companion object {
        private val defaultThreadRunner: ThreadRunner = { block -> thread(isDaemon = true, name = UpdateInfoUpdater::class.qualifiedName, block = block) }
    }
}

typealias ThreadRunner = (BackgroundProcess) -> Unit
typealias BackgroundProcess = () -> Unit
