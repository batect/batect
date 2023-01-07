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

package batect.wrapper

import batect.VersionInfo
import batect.logging.Logger
import batect.logging.data
import batect.primitives.Version
import batect.telemetry.TelemetryCaptor
import batect.telemetry.addUnhandledExceptionEvent
import java.time.ZonedDateTime
import kotlin.concurrent.thread

class WrapperCacheCleanupTask(
    private val enabled: Boolean,
    private val wrapperCache: WrapperCache,
    private val versionInfo: VersionInfo,
    private val telemetryCaptor: TelemetryCaptor,
    private val logger: Logger,
    private val threadRunner: ThreadRunner = defaultThreadRunner,
    private val timeSource: TimeSource = ZonedDateTime::now,
) {
    fun start() {
        if (!enabled) {
            logger.info {
                message("Wrapper cache cleanup disabled, not running cleanup task.")
            }

            return
        }

        threadRunner {
            val allVersions = wrapperCache.getCachedVersions()

            allVersions.forEach { processCachedWrapper(it, allVersions) }
        }
    }

    private fun processCachedWrapper(wrapperVersion: CachedWrapperVersion, allWrapperVersions: Set<CachedWrapperVersion>) {
        logger.info {
            message("Examining candidate version for deletion.")
            data("version", wrapperVersion.version)
            data("lastUsed", wrapperVersion.lastUsed)
            data("versionDirectory", wrapperVersion.cacheDirectory)
        }

        if (wrapperVersion.version >= versionInfo.version) {
            logger.info {
                message("Version not eligible for deletion, it is the same version as the current application or newer.")
                data("version", wrapperVersion.version)
                data("applicationVersion", versionInfo.version)
            }

            return
        }

        var dateToUse = wrapperVersion.lastUsed

        if (dateToUse == null) {
            val nextClosestWrapperVersionLastUseDate = allWrapperVersions.nextClosestVersionLastUseDate(wrapperVersion.version)

            if (nextClosestWrapperVersionLastUseDate == null) {
                logger.info {
                    message("Version not eligible for deletion, it has no last used date information and there is no more recent version with a last use date.")
                    data("version", wrapperVersion.version)
                }

                return
            }

            dateToUse = nextClosestWrapperVersionLastUseDate

            logger.info {
                message("Version has no last used date, using last used date from next closest version.")
                data("version", wrapperVersion.version)
                data("dateToUse", dateToUse)
            }
        }

        if (dateToUse >= timeSource().minusDays(30)) {
            logger.info {
                message("Version is not eligible for deletion because it has been used recently (or because the next closest version has been used recently), skipping.")
                data("version", wrapperVersion.version)
                data("dateToUse", dateToUse)
            }

            return
        }

        logger.info {
            message("Version is eligible for deletion, deleting.")
            data("version", wrapperVersion.version)
        }

        try {
            wrapperCache.delete(wrapperVersion)
        } catch (e: Throwable) {
            logger.warn {
                message("Deleting version failed.")
                exception(e)
                data("version", wrapperVersion.version)
            }

            telemetryCaptor.addUnhandledExceptionEvent(e, isUserFacing = false)
        }
    }

    private fun Set<CachedWrapperVersion>.nextClosestVersionLastUseDate(version: Version): ZonedDateTime? = this
        .filter { it.version > version && it.lastUsed != null }
        .minByOrNull { it.version }
        ?.lastUsed

    companion object {
        private val defaultThreadRunner: ThreadRunner = { block -> thread(isDaemon = true, name = WrapperCacheCleanupTask::class.qualifiedName, block = block) }
    }
}

typealias TimeSource = () -> ZonedDateTime
typealias ThreadRunner = (BackgroundProcess) -> Unit
typealias BackgroundProcess = () -> Unit
