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

package batect.wrapper

import batect.VersionInfo
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.utils.Version
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.ZoneOffset
import java.time.ZonedDateTime

object WrapperCacheCleanupTaskSpec : Spek({
    describe("a wrapper cache cleanup task") {
        val wrapperCache by createForEachTest { mock<WrapperCache>() }
        val currentApplicationVersion = Version(1, 0, 0)
        val versionInfo by createForEachTest {
            mock<VersionInfo> {
                on { version } doReturn currentApplicationVersion
            }
        }

        val logger by createLoggerForEachTest()
        val now = ZonedDateTime.of(2020, 5, 13, 6, 30, 0, 0, ZoneOffset.UTC)
        val timeSource: TimeSource = { now }

        given("cleaning up the wrapper cache is enabled") {
            val threadRunner: ThreadRunner = { block -> block() }
            val cleanupTask by createForEachTest { WrapperCacheCleanupTask(true, wrapperCache, versionInfo, logger, threadRunner, timeSource) }

            fun runTaskWithCachedVersions(vararg versions: CachedWrapperVersion) {
                whenever(wrapperCache.getCachedVersions()).doReturn(versions.toSet())

                cleanupTask.start()
            }

            val wrapperCachePath = Jimfs.newFileSystem(Configuration.unix()).getPath("/wrapper")
            val moreThanThirtyDaysAgo = now.minusDays(30).minusSeconds(1)
            val lessThanThirtyDaysAgo = now.minusDays(30).plusSeconds(1)

            given("no versions are cached") {
                beforeEachTest {
                    runTaskWithCachedVersions()
                }

                it("does not delete any versions") {
                    verify(wrapperCache, never()).delete(any())
                }
            }

            given("one version is cached") {
                given("it is newer than the current application version") {
                    val version = Version(1, 1, 0)

                    given("it was last used more than 30 days ago") {
                        val wrapperVersion = CachedWrapperVersion(version, moreThanThirtyDaysAgo, wrapperCachePath)

                        beforeEachTest {
                            runTaskWithCachedVersions(wrapperVersion)
                        }

                        it("does not delete any versions") {
                            verify(wrapperCache, never()).delete(any())
                        }
                    }

                    given("it has no last used date") {
                        val wrapperVersion = CachedWrapperVersion(version, null, wrapperCachePath)

                        beforeEachTest {
                            runTaskWithCachedVersions(wrapperVersion)
                        }

                        it("does not delete any versions") {
                            verify(wrapperCache, never()).delete(any())
                        }
                    }
                }

                given("it is the same as the current application version") {
                    given("it was last used more than 30 days ago") {
                        val wrapperVersion = CachedWrapperVersion(currentApplicationVersion, moreThanThirtyDaysAgo, wrapperCachePath)

                        beforeEachTest {
                            runTaskWithCachedVersions(wrapperVersion)
                        }

                        it("does not delete any versions") {
                            verify(wrapperCache, never()).delete(any())
                        }
                    }

                    given("it has no last used date") {
                        val wrapperVersion = CachedWrapperVersion(currentApplicationVersion, null, wrapperCachePath)

                        beforeEachTest {
                            runTaskWithCachedVersions(wrapperVersion)
                        }

                        it("does not delete any versions") {
                            verify(wrapperCache, never()).delete(any())
                        }
                    }
                }

                given("it is older than the current application version") {
                    val version = Version(0, 9, 0)

                    given("it was last used more than 30 days ago") {
                        val wrapperVersion = CachedWrapperVersion(version, moreThanThirtyDaysAgo, wrapperCachePath)

                        beforeEachTest {
                            runTaskWithCachedVersions(wrapperVersion)
                        }

                        it("deletes the version") {
                            verify(wrapperCache).delete(wrapperVersion)
                        }
                    }

                    given("it was last used less than 30 days ago") {
                        val wrapperVersion = CachedWrapperVersion(version, lessThanThirtyDaysAgo, wrapperCachePath)

                        beforeEachTest {
                            runTaskWithCachedVersions(wrapperVersion)
                        }

                        it("does not delete any versions") {
                            verify(wrapperCache, never()).delete(any())
                        }
                    }

                    given("it has no last used date") {
                        val wrapperVersion = CachedWrapperVersion(version, null, wrapperCachePath)

                        beforeEachTest {
                            runTaskWithCachedVersions(wrapperVersion)
                        }

                        it("does not delete any versions") {
                            verify(wrapperCache, never()).delete(any())
                        }
                    }
                }
            }

            given("two versions are cached, both of which are older than the current application version") {
                fun version1With(lastUsed: ZonedDateTime?) = CachedWrapperVersion(Version(0, 9, 0), lastUsed, wrapperCachePath)
                fun version2With(lastUsed: ZonedDateTime?) = CachedWrapperVersion(Version(0, 8, 0), lastUsed, wrapperCachePath)

                given("both were last used more than 30 days ago") {
                    val version1 = version1With(moreThanThirtyDaysAgo)
                    val version2 = version2With(moreThanThirtyDaysAgo)

                    beforeEachTest {
                        runTaskWithCachedVersions(version1, version2)
                    }

                    it("deletes both versions") {
                        verify(wrapperCache).delete(version1)
                        verify(wrapperCache).delete(version2)
                    }
                }

                given("one was last used more than 30 days ago, and the other was used less than 30 days ago") {
                    val version1 = version1With(moreThanThirtyDaysAgo)
                    val version2 = version2With(lessThanThirtyDaysAgo)

                    beforeEachTest {
                        runTaskWithCachedVersions(version1, version2)
                    }

                    it("deletes the version last used more than 30 days ago") {
                        verify(wrapperCache).delete(version1)
                    }

                    it("does not delete the version last used less than 30 days ago") {
                        verify(wrapperCache, never()).delete(version2)
                    }
                }

                given("the older version has no last used date") {
                    val version2 = version2With(null)

                    given("the newer version was last used less than 30 days ago") {
                        val version1 = version1With(lessThanThirtyDaysAgo)

                        beforeEachTest {
                            runTaskWithCachedVersions(version1, version2)
                        }

                        it("does not delete any versions") {
                            verify(wrapperCache, never()).delete(any())
                        }
                    }

                    given("the newer version was last used more than 30 days ago") {
                        val version1 = version1With(moreThanThirtyDaysAgo)

                        given("both can be deleted successfully") {
                            beforeEachTest {
                                runTaskWithCachedVersions(version1, version2)
                            }

                            it("deletes both versions") {
                                verify(wrapperCache).delete(version1)
                                verify(wrapperCache).delete(version2)
                            }
                        }

                        given("the first version fails to delete") {
                            beforeEachTest {
                                whenever(wrapperCache.delete(version1)).doThrow(RuntimeException("Something went wrong."))

                                runTaskWithCachedVersions(version1, version2)
                            }

                            it("attempts to delete both versions") {
                                verify(wrapperCache).delete(version1)
                                verify(wrapperCache).delete(version2)
                            }
                        }

                        given("the second version fails to delete") {
                            beforeEachTest {
                                whenever(wrapperCache.delete(version2)).doThrow(RuntimeException("Something went wrong."))

                                runTaskWithCachedVersions(version1, version2)
                            }

                            it("attempts to delete both versions") {
                                verify(wrapperCache).delete(version1)
                                verify(wrapperCache).delete(version2)
                            }
                        }
                    }
                }
            }

            given("multiple versions are cached, and one version does not have a last used date") {
                val olderVersion = CachedWrapperVersion(Version(0, 1, 0), moreThanThirtyDaysAgo, wrapperCachePath)
                val versionWithNoLastUsedDate = CachedWrapperVersion(Version(0, 2, 0), null, wrapperCachePath)
                val muchNewerVersion = CachedWrapperVersion(Version(0, 4, 0), moreThanThirtyDaysAgo, wrapperCachePath)

                given("the next newest version was last used more than 30 days ago") {
                    val nextNewestVersion = CachedWrapperVersion(Version(0, 3, 0), moreThanThirtyDaysAgo, wrapperCachePath)

                    beforeEachTest {
                        runTaskWithCachedVersions(olderVersion, nextNewestVersion, versionWithNoLastUsedDate, muchNewerVersion)
                    }

                    it("deletes the version with no last used date") {
                        verify(wrapperCache).delete(versionWithNoLastUsedDate)
                    }
                }

                given("the next newest version was last used less than 30 days ago") {
                    val nextNewestVersion = CachedWrapperVersion(Version(0, 3, 0), lessThanThirtyDaysAgo, wrapperCachePath)

                    beforeEachTest {
                        runTaskWithCachedVersions(olderVersion, nextNewestVersion, versionWithNoLastUsedDate, muchNewerVersion)
                    }

                    it("does not delete the version with no last used date") {
                        verify(wrapperCache, never()).delete(versionWithNoLastUsedDate)
                    }
                }
            }
        }

        given("cleaning up the wrapper cache is disabled") {
            var threadStarted: Boolean = false
            val threadRunner: ThreadRunner = { block ->
                threadStarted = true
                block()
            }

            val cleanupTask by createForEachTest { WrapperCacheCleanupTask(false, wrapperCache, versionInfo, logger, threadRunner, timeSource) }

            beforeEachTest { threadStarted = false }

            on("attempting to start the task") {
                runForEachTest { cleanupTask.start() }

                it("does not start a thread") {
                    assertThat(threadStarted, equalTo(false))
                }

                it("does not list the available versions") {
                    verify(wrapperCache, never()).getCachedVersions()
                }
            }
        }
    }
})
