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

package batect.wrapper

import batect.logging.Logger
import batect.logging.Severity
import batect.os.HostEnvironmentVariables
import batect.primitives.Version
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.InMemoryLogSink
import batect.testutils.logging.hasMessage
import batect.testutils.logging.withAdditionalData
import batect.testutils.logging.withLogMessage
import batect.testutils.logging.withSeverity
import batect.testutils.runForEachTest
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isEmpty
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneOffset
import java.time.ZonedDateTime

object WrapperCacheSpec : Spek({
    describe("a wrapper cache") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
        val logSink by createForEachTest { InMemoryLogSink() }
        val logger by createForEachTest { Logger("logger", logSink) }

        given("a cache directory is provided") {
            val cacheDirectory by createForEachTest { fileSystem.getPath("/batect-caches") }
            val wrapperCache by createForEachTest { WrapperCache(fileSystem, environmentVariablesFor(cacheDirectory), logger) }

            given("the cache directory exists") {
                beforeEachTest {
                    Files.createDirectories(cacheDirectory)
                }

                describe("setting the last used time for a version") {
                    val version = Version(1, 2, 3, "abc", "meta")
                    val time = ZonedDateTime.of(2020, 5, 10, 9, 50, 12, 123456789, ZoneOffset.ofHours(8))
                    val versionDirectory by createForEachTest { cacheDirectory.resolve("1.2.3-abc+meta") }

                    given("the directory for the version does not exist") {
                        beforeEachTest { wrapperCache.setLastUsedForVersion(version, time) }

                        it("does not create the directory") {
                            assertThat(Files.exists(versionDirectory), equalTo(false))
                        }

                        it("logs a warning") {
                            assertThat(
                                logSink,
                                hasMessage(
                                    withLogMessage("Cache directory for version does not exist, not storing last used time.") and
                                        withAdditionalData("version", version) and
                                        withAdditionalData("versionDirectory", versionDirectory.toString()) and
                                        withSeverity(Severity.Warning)
                                )
                            )
                        }
                    }

                    given("the directory for the version does exist") {
                        beforeEachTest {
                            Files.createDirectories(versionDirectory)

                            wrapperCache.setLastUsedForVersion(version, time)
                        }

                        it("writes the last used time to the version's directory") {
                            assertThat(Files.readAllLines(versionDirectory.resolve("lastUsed"), Charsets.UTF_8), equalTo(listOf("2020-05-10T01:50:12.123456789Z")))
                        }
                    }
                }

                describe("getting details of all cached versions") {
                    given("the cache directory is empty") {
                        val result by runForEachTest { wrapperCache.getCachedVersions() }

                        it("returns an empty set of versions") {
                            assertThat(result, isEmpty)
                        }
                    }

                    given("the cache directory contains a single version directory") {
                        val version = Version(1, 2, 3)
                        val versionDirectory by createForEachTest { cacheDirectory.resolve("1.2.3") }

                        beforeEachTest {
                            Files.createDirectories(versionDirectory)
                        }

                        given("the version directory does not contain a last used time file") {
                            val result by runForEachTest { wrapperCache.getCachedVersions() }

                            it("returns the version with no last used time") {
                                assertThat(result, equalTo(setOf(CachedWrapperVersion(version, null, versionDirectory))))
                            }

                            it("logs a warning") {
                                assertThat(
                                    logSink,
                                    hasMessage(
                                        withLogMessage("Version cache directory does not contain a last used time file.") and
                                            withAdditionalData("version", version) and
                                            withAdditionalData("versionDirectory", versionDirectory.toString()) and
                                            withSeverity(Severity.Warning)
                                    )
                                )
                            }
                        }

                        given("the version directory contains a last used time file") {
                            val lastUsedFilePath by createForEachTest { versionDirectory.resolve("lastUsed") }

                            given("the file contains a valid time") {
                                beforeEachTest {
                                    Files.write(lastUsedFilePath, listOf("2020-05-10T11:12:13.123456789Z"))
                                }

                                val result by runForEachTest { wrapperCache.getCachedVersions() }

                                it("returns the version with the last used time from the file") {
                                    assertThat(result, equalTo(setOf(CachedWrapperVersion(version, ZonedDateTime.of(2020, 5, 10, 11, 12, 13, 123456789, ZoneOffset.UTC), versionDirectory))))
                                }
                            }

                            given("the file does not contain a valid time") {
                                beforeEachTest {
                                    Files.write(lastUsedFilePath, listOf("blah"))
                                }

                                val result by runForEachTest { wrapperCache.getCachedVersions() }

                                it("returns the version with no last used time") {
                                    assertThat(result, equalTo(setOf(CachedWrapperVersion(version, null, versionDirectory))))
                                }

                                it("logs a warning") {
                                    assertThat(
                                        logSink,
                                        hasMessage(
                                            withLogMessage("Last used time file does not contain a valid time, ignoring.") and
                                                withAdditionalData("version", version) and
                                                withAdditionalData("versionDirectory", versionDirectory.toString()) and
                                                withAdditionalData("lastUsedFilePath", lastUsedFilePath.toString()) and
                                                withSeverity(Severity.Warning)
                                        )
                                    )
                                }
                            }

                            given("the file is empty") {
                                beforeEachTest { Files.createFile(lastUsedFilePath) }

                                val result by runForEachTest { wrapperCache.getCachedVersions() }

                                it("returns the version with no last used time") {
                                    assertThat(result, equalTo(setOf(CachedWrapperVersion(version, null, versionDirectory))))
                                }

                                it("logs a warning") {
                                    assertThat(
                                        logSink,
                                        hasMessage(
                                            withLogMessage("Last used time file does not contain a valid time, ignoring.") and
                                                withAdditionalData("version", version) and
                                                withAdditionalData("versionDirectory", versionDirectory.toString()) and
                                                withAdditionalData("lastUsedFilePath", lastUsedFilePath.toString()) and
                                                withSeverity(Severity.Warning)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    given("the cache directory contains two version directories, each with a valid last used date") {
                        val version1 = Version(1, 2, 3)
                        val version2 = Version(1, 3, 4)

                        val version1Directory by createForEachTest { cacheDirectory.resolve("1.2.3") }
                        val version2Directory by createForEachTest { cacheDirectory.resolve("1.3.4") }

                        beforeEachTest {
                            Files.createDirectories(version1Directory)
                            Files.createDirectories(version2Directory)

                            Files.write(version1Directory.resolve("lastUsed"), listOf("2020-05-10T11:12:13.123456789Z"))
                            Files.write(version2Directory.resolve("lastUsed"), listOf("2021-06-10T11:12:13.123456789Z"))
                        }

                        val result by runForEachTest { wrapperCache.getCachedVersions() }

                        it("returns both versions") {
                            assertThat(
                                result,
                                equalTo(
                                    setOf(
                                        CachedWrapperVersion(version1, ZonedDateTime.of(2020, 5, 10, 11, 12, 13, 123456789, ZoneOffset.UTC), version1Directory),
                                        CachedWrapperVersion(version2, ZonedDateTime.of(2021, 6, 10, 11, 12, 13, 123456789, ZoneOffset.UTC), version2Directory)
                                    )
                                )
                            )
                        }
                    }

                    given("the cache directory contains a version directory and a directory with another name") {
                        val version = Version(1, 2, 3)
                        val versionDirectory by createForEachTest { cacheDirectory.resolve("1.2.3") }
                        val nonVersionDirectory by createForEachTest { cacheDirectory.resolve("not a version") }

                        beforeEachTest {
                            Files.createDirectories(versionDirectory)
                            Files.write(versionDirectory.resolve("lastUsed"), listOf("2020-05-10T11:12:13.123456789Z"))

                            Files.createDirectories(nonVersionDirectory)
                        }

                        val result by runForEachTest { wrapperCache.getCachedVersions() }

                        it("returns the version") {
                            assertThat(result, equalTo(setOf(CachedWrapperVersion(version, ZonedDateTime.of(2020, 5, 10, 11, 12, 13, 123456789, ZoneOffset.UTC), versionDirectory))))
                        }

                        it("logs a warning") {
                            assertThat(
                                logSink,
                                hasMessage(
                                    withLogMessage("Directory name cannot be parsed as a version, ignoring directory.") and
                                        withAdditionalData("directory", nonVersionDirectory.toString()) and
                                        withSeverity(Severity.Warning)
                                )
                            )
                        }
                    }
                }
            }

            given("the cache directory does not exist") {
                describe("setting the last used time for a version") {
                    val version = Version(1, 2, 3, "abc", "meta")
                    val time = ZonedDateTime.of(2020, 5, 10, 9, 50, 12, 123456789, ZoneOffset.UTC)
                    val versionDirectory by createForEachTest { cacheDirectory.resolve("1.2.3-abc+meta") }

                    beforeEachTest { wrapperCache.setLastUsedForVersion(version, time) }

                    it("does not create the directory") {
                        assertThat(Files.exists(versionDirectory), equalTo(false))
                    }

                    it("logs a warning") {
                        assertThat(
                            logSink,
                            hasMessage(
                                withLogMessage("Cache directory for version does not exist, not storing last used time.") and
                                    withAdditionalData("version", version) and
                                    withAdditionalData("versionDirectory", versionDirectory.toString()) and
                                    withSeverity(Severity.Warning)
                            )
                        )
                    }
                }

                describe("getting details of all cached versions") {
                    val result by runForEachTest { wrapperCache.getCachedVersions() }

                    it("returns an empty set") {
                        assertThat(result, isEmpty)
                    }

                    it("logs a warning") {
                        assertThat(
                            logSink,
                            hasMessage(
                                withLogMessage("Cache directory does not exist, returning empty list of versions.") and
                                    withAdditionalData("cacheDirectory", cacheDirectory.toString()) and
                                    withSeverity(Severity.Warning)
                            )
                        )
                    }
                }
            }
        }

        given("a cache directory is not provided") {
            val wrapperCache by createForEachTest { WrapperCache(fileSystem, HostEnvironmentVariables(), logger) }

            describe("setting the last used time for a version") {
                val version = Version(1, 2, 3, "abc", "meta")
                val time = ZonedDateTime.of(2020, 5, 10, 9, 50, 12, 123456789, ZoneOffset.UTC)

                beforeEachTest { wrapperCache.setLastUsedForVersion(version, time) }

                it("logs a warning") {
                    assertThat(logSink, hasMessage(withLogMessage("Wrapper cache directory environment variable (BATECT_WRAPPER_CACHE_DIR) not set, not storing last used time.") and withSeverity(Severity.Warning)))
                }
            }

            describe("getting details of all cached versions") {
                val result by runForEachTest { wrapperCache.getCachedVersions() }

                it("returns an empty set") {
                    assertThat(result, isEmpty)
                }

                it("logs a warning") {
                    assertThat(logSink, hasMessage(withLogMessage("Wrapper cache directory environment variable (BATECT_WRAPPER_CACHE_DIR) not set, returning empty list of versions.") and withSeverity(Severity.Warning)))
                }
            }
        }

        describe("deleting a cached version") {
            val version = Version(1, 2, 3)
            val versionDirectory by createForEachTest { fileSystem.getPath("/version-1.2.3") }
            val wrapperVersion by createForEachTest { CachedWrapperVersion(version, null, versionDirectory) }
            val wrapperCache by createForEachTest { WrapperCache(fileSystem, HostEnvironmentVariables(), logger) }

            beforeEachTest { Files.createDirectories(versionDirectory) }

            given("the version cache directory is empty") {
                beforeEachTest { wrapperCache.delete(wrapperVersion) }

                it("deletes the version's directory") {
                    assertThat(Files.exists(versionDirectory), equalTo(false))
                }
            }

            given("the version cache directory contains files") {
                beforeEachTest {
                    Files.createFile(versionDirectory.resolve("file1"))
                    Files.createFile(versionDirectory.resolve("file2"))
                }

                given("the version cache directory does not contain a last used time file") {
                    beforeEachTest { wrapperCache.delete(wrapperVersion) }

                    it("deletes the version's directory") {
                        assertThat(Files.exists(versionDirectory), equalTo(false))
                    }
                }

                given("the version cache directory does contain a last used time file") {
                    // FIXME: Originally this test used a WatchService to observe the file deletion order, but WatchService in Jimfs does not behave correctly when the directory being watched
                    // is deleted (see https://github.com/google/jimfs/issues/72).

                    val lastUsedFilePath by createForEachTest { versionDirectory.resolve("lastUsed") }

                    beforeEachTest {
                        Files.createFile(lastUsedFilePath)
                        wrapperCache.delete(wrapperVersion)
                    }

                    it("deletes the version's directory") {
                        assertThat(Files.exists(versionDirectory), equalTo(false))
                    }
                }
            }
        }
    }
})

private fun environmentVariablesFor(cacheDirectory: Path) = HostEnvironmentVariables("BATECT_WRAPPER_CACHE_DIR" to cacheDirectory.toString())
