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

import batect.logging.Logger
import batect.logging.Severity
import batect.os.HostEnvironmentVariables
import batect.testutils.InMemoryLogSink
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.hasMessage
import batect.testutils.withAdditionalData
import batect.testutils.withLogMessage
import batect.testutils.withSeverity
import batect.utils.Version
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
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
                            assertThat(logSink,
                                hasMessage(
                                    withLogMessage("Cache directory for version does not exist, not storing last used time.") and
                                        withAdditionalData("version", version) and
                                        withAdditionalData("directory", versionDirectory.toString()) and
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
                        assertThat(logSink,
                            hasMessage(
                                withLogMessage("Cache directory for version does not exist, not storing last used time.") and
                                    withAdditionalData("version", version) and
                                    withAdditionalData("directory", versionDirectory.toString()) and
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
        }
    }
})

private fun environmentVariablesFor(cacheDirectory: Path) = HostEnvironmentVariables("BATECT_WRAPPER_CACHE_DIR" to cacheDirectory.toString())
