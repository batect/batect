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

package batect.telemetry

import batect.logging.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import kotlin.concurrent.thread
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

class TelemetryUploadTask(
    private val telemetryConsent: TelemetryConsent,
    private val telemetryUploadQueue: TelemetryUploadQueue,
    private val abacusClient: AbacusClient,
    private val telemetrySessionBuilder: TelemetrySessionBuilder,
    private val logger: Logger,
    private val threadRunner: ThreadRunner = defaultThreadRunner,
    private val timeSource: TimeSource = ZonedDateTime::now
) {
    private val json = Json(JsonConfiguration.Stable)

    fun start() {
        if (!telemetryConsent.telemetryAllowed) {
            logger.info {
                message("Telemetry not allowed, not starting telemetry upload task.")
            }

            return
        }

        threadRunner(::runOnThread)
    }

    private fun runOnThread() {
        try {
            val sessionPaths = telemetryUploadQueue.getAll()

            if (sessionPaths.isEmpty()) {
                logger.info {
                    message("No sessions to upload.")
                }

                return
            }

            sessionPaths.shuffled().forEach { upload(it) }
        } catch (e: Throwable) {
            logger.error {
                message("Unhandled exception while uploading telemetry sessions.")
                exception(e)
            }
        }
    }

    private fun upload(sessionPath: Path) {
        logger.info {
            message("Uploading session.")
            data("sessionPath", sessionPath)
        }

        val bytes = Files.readAllBytes(sessionPath)

        try {
            abacusClient.upload(bytes)
            telemetryUploadQueue.pop(sessionPath)

            logger.info {
                message("Session uploaded successfully.")
                data("sessionPath", sessionPath)
            }
        } catch (e: Throwable) {
            telemetrySessionBuilder.addUnhandledExceptionEvent(e, isUserFacing = false)

            handleFailedUpload(sessionPath, bytes, e)
        }
    }

    private fun handleFailedUpload(sessionPath: Path, bytes: ByteArray, uploadException: Throwable) {
        val session = try {
            json.parse(TelemetrySession.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (parsingException: Throwable) {
            logger.error {
                message("Session upload failed, and parsing session to determine age failed.")
                data("sessionPath", sessionPath)
                exception("uploadException", uploadException)
                exception("parsingException", parsingException)
            }

            telemetrySessionBuilder.addUnhandledExceptionEvent(parsingException, isUserFacing = false)

            return
        }

        val now = timeSource()
        val threshold = now.minusDays(30)

        if (session.sessionStartTime >= threshold) {
            logger.warn {
                message("Session upload failed. Session is less than 30 days old and so won't be deleted.")
                data("sessionPath", sessionPath)
                exception(uploadException)
            }

            return
        }

        telemetryUploadQueue.pop(sessionPath)

        logger.warn {
            message("Session upload failed. Session is more than 30 days old and so has been deleted.")
            data("sessionPath", sessionPath)
            exception(uploadException)
        }

        telemetrySessionBuilder.addEvent("DeletedOldTelemetrySessionThatFailedToUpload", emptyMap())
    }

    companion object {
        private val defaultThreadRunner: ThreadRunner = { block -> thread(isDaemon = true, name = TelemetryUploadTask::class.qualifiedName, block = block) }
    }
}

typealias ThreadRunner = (BackgroundProcess) -> Unit
typealias BackgroundProcess = () -> Unit
typealias TimeSource = () -> ZonedDateTime
