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

import batect.VersionInfo
import batect.primitives.Version
import batect.telemetry.CommonAttributes
import batect.telemetry.CommonEvents
import batect.telemetry.TestTelemetryCaptor
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.on
import batect.ui.Console
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.hasSize
import kotlinx.serialization.json.JsonPrimitive
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.ZoneOffset
import java.time.ZonedDateTime

object UpdateNotifierSpec : Spek({
    describe("an update notifier") {
        val updateInfoStorage by createForEachTest { mock<UpdateInfoStorage>() }
        val updateInfoUpdater by createForEachTest { mock<UpdateInfoUpdater>() }
        val versionInfo by createForEachTest { mock<VersionInfo>() }
        val console by createForEachTest { mock<Console>() }
        val telemetryCaptor by createForEachTest { TestTelemetryCaptor() }
        val logger by createLoggerForEachTest()
        var currentTime = ZonedDateTime.of(0, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

        beforeEachTest {
            currentTime = ZonedDateTime.of(0, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        }

        on("when the update notification is disabled") {
            beforeEachTest {
                val updateNotifier = UpdateNotifier(true, updateInfoStorage, updateInfoUpdater, versionInfo, console, telemetryCaptor, logger, { currentTime })
                updateNotifier.run()
            }

            it("does not attempt to load the cached update info") {
                verify(updateInfoStorage, never()).read()
            }

            it("does not print anything to the console") {
                verifyNoInteractions(console)
            }

            it("does not trigger an update of the cached update info") {
                verify(updateInfoUpdater, never()).updateCachedInfo()
            }
        }

        describe("when the update notification is enabled") {
            val updateNotifier by createForEachTest { UpdateNotifier(false, updateInfoStorage, updateInfoUpdater, versionInfo, console, telemetryCaptor, logger, { currentTime }) }

            on("when no cached update information is available") {
                beforeEachTest {
                    whenever(updateInfoStorage.read()).thenReturn(null)
                    updateNotifier.run()
                }

                it("does not print anything to the console") {
                    verifyNoInteractions(console)
                }

                it("triggers an update of the cached update info") {
                    verify(updateInfoUpdater).updateCachedInfo()
                }
            }

            on("when the cached update information can't be read") {
                val exception = RuntimeException("Something went wrong")

                beforeEachTest {
                    whenever(updateInfoStorage.read()).thenThrow(exception)
                    updateNotifier.run()
                }

                it("does not print anything to the console") {
                    verifyNoInteractions(console)
                }

                it("triggers an update of the cached update info") {
                    verify(updateInfoUpdater).updateCachedInfo()
                }

                it("reports the exception in telemetry") {
                    assertThat(telemetryCaptor.allEvents, hasSize(equalTo(1)))

                    val event = telemetryCaptor.allEvents.single()
                    assertThat(event.type, equalTo(CommonEvents.UnhandledException))
                    assertThat(event.attributes[CommonAttributes.ExceptionCaughtAt], equalTo(JsonPrimitive("batect.updates.UpdateNotifier.tryToLoadCachedInfo")))
                    assertThat(event.attributes[CommonAttributes.IsUserFacingException], equalTo(JsonPrimitive(false)))
                }
            }

            describe("when the cached update information can be read") {
                val lastUpdated = ZonedDateTime.of(2017, 10, 3, 1, 10, 0, 0, ZoneOffset.UTC)
                val updateInfo = UpdateInfo(Version(0, 3, 0), "https://something.com/batect/0.3", lastUpdated, emptyList())

                beforeEachTest {
                    whenever(updateInfoStorage.read()).thenReturn(updateInfo)
                }

                describe("and it has been updated in the last 36 hours") {
                    beforeEachTest {
                        currentTime = lastUpdated.plusHours(36).plusSeconds(-1)
                    }

                    on("and the version in the cached update information matches the current version") {
                        beforeEachTest {
                            whenever(versionInfo.version).thenReturn(Version(0, 3, 0))
                            updateNotifier.run()
                        }

                        it("does not print anything to the console") {
                            verifyNoInteractions(console)
                        }

                        it("does not trigger an update of the cached update info") {
                            verify(updateInfoUpdater, never()).updateCachedInfo()
                        }
                    }

                    on("and the version in the cached update information is older than the current version") {
                        beforeEachTest {
                            whenever(versionInfo.version).thenReturn(Version(0, 4, 0))
                            updateNotifier.run()
                        }

                        it("does not print anything to the console") {
                            verifyNoInteractions(console)
                        }

                        it("does not trigger an update of the cached update info") {
                            verify(updateInfoUpdater, never()).updateCachedInfo()
                        }
                    }

                    on("and the version in the cached update information is newer than the current version") {
                        beforeEachTest {
                            whenever(versionInfo.version).thenReturn(Version(0, 2, 0))
                            updateNotifier.run()
                        }

                        it("prints a message to the console") {
                            inOrder(console) {
                                verify(console).println("Version 0.3.0 of Batect is now available (you have 0.2.0).")
                                verify(console).println("To upgrade to the latest version, run './batect --upgrade'.")
                                verify(console).println("For more information, visit https://something.com/batect/0.3.")
                                verify(console).println()
                            }
                        }

                        it("reports an event in telemetry") {
                            assertThat(telemetryCaptor.allEvents, hasSize(equalTo(1)))

                            val event = telemetryCaptor.allEvents.single()
                            assertThat(event.type, equalTo("UpdateAvailableNotificationShown"))
                            assertThat(event.attributes["currentVersion"], equalTo(JsonPrimitive("0.2.0")))
                            assertThat(event.attributes["newVersion"], equalTo(JsonPrimitive("0.3.0")))
                        }

                        it("does not trigger an update of the cached update info") {
                            verify(updateInfoUpdater, never()).updateCachedInfo()
                        }
                    }
                }

                describe("and it has not been updated in the last 36 hours") {
                    beforeEachTest {
                        currentTime = lastUpdated.plusHours(36).plusSeconds(1)
                    }

                    on("and the version in the cached update information matches the current version") {
                        beforeEachTest {
                            whenever(versionInfo.version).thenReturn(Version(0, 3, 0))
                            updateNotifier.run()
                        }

                        it("does not print anything to the console") {
                            verifyNoInteractions(console)
                        }

                        it("triggers an update of the cached update info") {
                            verify(updateInfoUpdater).updateCachedInfo()
                        }
                    }

                    on("and the version in the cached update information is older than the current version") {
                        beforeEachTest {
                            whenever(versionInfo.version).thenReturn(Version(0, 4, 0))
                            updateNotifier.run()
                        }

                        it("does not print anything to the console") {
                            verifyNoInteractions(console)
                        }

                        it("triggers an update of the cached update info") {
                            verify(updateInfoUpdater).updateCachedInfo()
                        }
                    }

                    on("and the version in the cached update information is newer than the current version") {
                        beforeEachTest {
                            whenever(versionInfo.version).thenReturn(Version(0, 2, 0))
                            updateNotifier.run()
                        }

                        it("prints a message to the console") {
                            inOrder(console) {
                                verify(console).println("Version 0.3.0 of Batect is now available (you have 0.2.0).")
                                verify(console).println("To upgrade to the latest version, run './batect --upgrade'.")
                                verify(console).println("For more information, visit https://something.com/batect/0.3.")
                                verify(console).println()
                            }
                        }

                        it("triggers an update of the cached update info") {
                            verify(updateInfoUpdater).updateCachedInfo()
                        }

                        it("reports an event in telemetry") {
                            assertThat(telemetryCaptor.allEvents, hasSize(equalTo(1)))

                            val event = telemetryCaptor.allEvents.single()
                            assertThat(event.type, equalTo("UpdateAvailableNotificationShown"))
                            assertThat(event.attributes["currentVersion"], equalTo(JsonPrimitive("0.2.0")))
                            assertThat(event.attributes["newVersion"], equalTo(JsonPrimitive("0.3.0")))
                        }
                    }
                }
            }
        }
    }
})
