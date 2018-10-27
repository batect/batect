/*
   Copyright 2017-2018 Charles Korn.

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
import batect.logging.Severity
import batect.testutils.InMemoryLogSink
import batect.testutils.createForEachTest
import batect.testutils.hasMessage
import batect.testutils.withException
import batect.testutils.withLogMessage
import batect.testutils.withSeverity
import batect.utils.Version
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.time.ZoneOffset
import java.time.ZonedDateTime

object UpdateInfoUpdaterSpec : Spek({
    describe("an update info updater") {
        val updateInfo = UpdateInfo(Version(0, 1, 2), "https://something.com/batect/0.1.2", ZonedDateTime.now(ZoneOffset.UTC), "https://something.com/batect/0.1.2/wrapper")
        val updateInfoDownloader by createForEachTest { mock<UpdateInfoDownloader>() }
        val updateInfoStorage by createForEachTest { mock<UpdateInfoStorage>() }
        val logSink by createForEachTest { InMemoryLogSink() }
        val logger by createForEachTest { Logger("some.source", logSink) }
        var backgroundProcess: BackgroundProcess? = null
        val threadRunner = { code: BackgroundProcess ->
            backgroundProcess = code
        }

        val updateInfoUpdater by createForEachTest { UpdateInfoUpdater(updateInfoDownloader, updateInfoStorage, logger, threadRunner) }

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
                backgroundProcess!!.invoke()

                it("writes the downloaded release information to disk") {
                    verify(updateInfoStorage).write(updateInfo)
                }
            }
        }

        on("downloading release information failing") {
            val exception = RuntimeException("Something went wrong")
            whenever(updateInfoDownloader.getLatestVersionInfo()).thenThrow(exception)

            updateInfoUpdater.updateCachedInfo()
            backgroundProcess!!.invoke()

            it("does not write anything to disk") {
                verify(updateInfoStorage, never()).write(any())
            }

            it("logs a warning") {
                assertThat(logSink, hasMessage(
                    withLogMessage("Could not update cached update information.")
                        and withSeverity(Severity.Warning)
                        and withException(exception)
                ))
            }
        }

        on("writing the updated release information to disk failing") {
            val exception = RuntimeException("Something went wrong")
            whenever(updateInfoDownloader.getLatestVersionInfo()).thenReturn(updateInfo)
            whenever(updateInfoStorage.write(updateInfo)).thenThrow(exception)

            updateInfoUpdater.updateCachedInfo()
            backgroundProcess!!.invoke()

            it("logs a warning") {
                assertThat(logSink, hasMessage(
                    withLogMessage("Could not update cached update information.")
                        and withSeverity(Severity.Warning)
                        and withException(exception)
                ))
            }
        }
    }
})
