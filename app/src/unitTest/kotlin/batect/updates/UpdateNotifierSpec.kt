/*
   Copyright 2017-2019 Charles Korn.

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

import batect.VersionInfo
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.on
import batect.ui.Console
import batect.utils.Version
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
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
        val logger by createLoggerForEachTest()
        var currentTime = ZonedDateTime.of(0, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

        beforeEachTest {
            currentTime = ZonedDateTime.of(0, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        }

        on("when the update notification is disabled") {
            beforeEachTest {
                val updateNotifier = UpdateNotifier(true, updateInfoStorage, updateInfoUpdater, versionInfo, console, logger, { currentTime })
                updateNotifier.run()
            }

            it("does not attempt to load the cached update info") {
                verify(updateInfoStorage, never()).read()
            }

            it("does not print anything to the console") {
                verifyZeroInteractions(console)
            }

            it("does not trigger an update of the cached update info") {
                verify(updateInfoUpdater, never()).updateCachedInfo()
            }
        }

        describe("when the update notification is enabled") {
            val updateNotifier by createForEachTest { UpdateNotifier(false, updateInfoStorage, updateInfoUpdater, versionInfo, console, logger, { currentTime }) }

            on("when no cached update information is available") {
                beforeEachTest {
                    whenever(updateInfoStorage.read()).thenReturn(null)
                    updateNotifier.run()
                }

                it("does not print anything to the console") {
                    verifyZeroInteractions(console)
                }

                it("triggers an update of the cached update info") {
                    verify(updateInfoUpdater).updateCachedInfo()
                }
            }

            on("when the cached update information can't be read") {
                beforeEachTest {
                    whenever(updateInfoStorage.read()).thenThrow(RuntimeException("Something went wrong"))
                    updateNotifier.run()
                }

                it("does not print anything to the console") {
                    verifyZeroInteractions(console)
                }

                it("triggers an update of the cached update info") {
                    verify(updateInfoUpdater).updateCachedInfo()
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
                            verifyZeroInteractions(console)
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
                            verifyZeroInteractions(console)
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
                                verify(console).println("Version 0.3 of batect is now available (you have 0.2).")
                                verify(console).println("To upgrade to the latest version, run './batect --upgrade'.")
                                verify(console).println("For more information, visit https://something.com/batect/0.3.")
                                verify(console).println()
                            }
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
                            verifyZeroInteractions(console)
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
                            verifyZeroInteractions(console)
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
                                verify(console).println("Version 0.3 of batect is now available (you have 0.2).")
                                verify(console).println("To upgrade to the latest version, run './batect --upgrade'.")
                                verify(console).println("For more information, visit https://something.com/batect/0.3.")
                                verify(console).println()
                            }
                        }

                        it("triggers an update of the cached update info") {
                            verify(updateInfoUpdater).updateCachedInfo()
                        }
                    }
                }
            }
        }
    }
})
