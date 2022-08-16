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

package batect.updates

import batect.logging.Logger
import batect.logging.Severity
import batect.primitives.Version
import batect.telemetry.CommonAttributes
import batect.telemetry.CommonEvents
import batect.telemetry.TestTelemetryCaptor
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.logging.InMemoryLogSink
import batect.testutils.logging.hasMessage
import batect.testutils.logging.withException
import batect.testutils.logging.withLogMessage
import batect.testutils.logging.withSeverity
import batect.testutils.on
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.hasSize
import kotlinx.serialization.json.JsonPrimitive
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.ZoneOffset
import java.time.ZonedDateTime

object UpdateInfoUpdaterSpec : Spek({
    describe("an update info updater") {
        val updateInfo = UpdateInfo(
            Version(0, 1, 2),
            "https://something.com/batect/0.1.2",
            ZonedDateTime.now(ZoneOffset.UTC),
            listOf(ScriptInfo("batect", "https://something.com/batect/0.1.2/wrapper"))
        )

        val updateInfoDownloader by createForEachTest { mock<UpdateInfoDownloader>() }
        val updateInfoStorage by createForEachTest { mock<UpdateInfoStorage>() }
        val telemetryCaptor by createForEachTest { TestTelemetryCaptor() }
        val logSink by createForEachTest { InMemoryLogSink() }
        val logger by createForEachTest { Logger("some.source", logSink) }
        var backgroundProcess: BackgroundProcess? = null
        val threadRunner = { code: BackgroundProcess ->
            backgroundProcess = code
        }

        val updateInfoUpdater by createForEachTest { UpdateInfoUpdater(updateInfoDownloader, updateInfoStorage, telemetryCaptor, logger, threadRunner) }

        beforeEachTest {
            backgroundProcess = null
        }

        describe("when downloading release information succeeds") {
            beforeEachTest {
                whenever(updateInfoDownloader.getLatestVersionInfo()).thenReturn(updateInfo)
                updateInfoUpdater.updateCachedInfo()
            }

            on("on the main thread") {
                it("does not call the downloader") {
                    verify(updateInfoDownloader, never()).getLatestVersionInfo()
                }
            }

            on("on the background thread") {
                beforeEachTest { backgroundProcess!!.invoke() }

                it("writes the downloaded release information to disk") {
                    verify(updateInfoStorage).write(updateInfo)
                }
            }
        }

        on("downloading release information failing") {
            val exception by createForEachTest { RuntimeException("Something went wrong") }

            beforeEachTest {
                whenever(updateInfoDownloader.getLatestVersionInfo()).thenThrow(exception)

                updateInfoUpdater.updateCachedInfo()
                backgroundProcess!!.invoke()
            }

            it("does not write anything to disk") {
                verify(updateInfoStorage, never()).write(any())
            }

            it("logs a warning") {
                assertThat(
                    logSink,
                    hasMessage(
                        withLogMessage("Could not update cached update information.")
                            and withSeverity(Severity.Warning)
                            and withException(exception)
                    )
                )
            }

            it("reports the exception in telemetry") {
                assertThat(telemetryCaptor.allEvents, hasSize(equalTo(1)))

                val event = telemetryCaptor.allEvents.single()
                assertThat(event.type, equalTo(CommonEvents.UnhandledException))
                assertThat(event.attributes[CommonAttributes.ExceptionCaughtAt], equalTo(JsonPrimitive("batect.updates.UpdateInfoUpdater\$updateCachedInfo\$1.invoke")))
                assertThat(event.attributes[CommonAttributes.IsUserFacingException], equalTo(JsonPrimitive(false)))
            }
        }

        on("writing the updated release information to disk failing") {
            val exception by createForEachTest { RuntimeException("Something went wrong") }

            beforeEachTest {
                whenever(updateInfoDownloader.getLatestVersionInfo()).thenReturn(updateInfo)
                whenever(updateInfoStorage.write(updateInfo)).thenThrow(exception)

                updateInfoUpdater.updateCachedInfo()
                backgroundProcess!!.invoke()
            }

            it("logs a warning") {
                assertThat(
                    logSink,
                    hasMessage(
                        withLogMessage("Could not update cached update information.")
                            and withSeverity(Severity.Warning)
                            and withException(exception)
                    )
                )
            }

            it("reports the exception in telemetry") {
                assertThat(telemetryCaptor.allEvents, hasSize(equalTo(1)))

                val event = telemetryCaptor.allEvents.single()
                assertThat(event.type, equalTo(CommonEvents.UnhandledException))
                assertThat(event.attributes[CommonAttributes.ExceptionCaughtAt], equalTo(JsonPrimitive("batect.updates.UpdateInfoUpdater\$updateCachedInfo\$1.invoke")))
                assertThat(event.attributes[CommonAttributes.IsUserFacingException], equalTo(JsonPrimitive(false)))
            }
        }
    }
})
