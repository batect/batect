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

package batect.config.includes

import batect.logging.Logger
import batect.logging.Severity
import batect.telemetry.CommonAttributes
import batect.telemetry.CommonEvents
import batect.telemetry.TestTelemetryCaptor
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.InMemoryLogSink
import batect.testutils.logging.hasMessage
import batect.testutils.logging.withAdditionalData
import batect.testutils.logging.withException
import batect.testutils.logging.withLogMessage
import batect.testutils.logging.withSeverity
import batect.testutils.osIndependentPath
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.hasSize
import kotlinx.serialization.json.JsonPrimitive
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.ZoneOffset
import java.time.ZonedDateTime

object GitRepositoryCacheCleanupTaskSpec : Spek({
    describe("a Git repository cache cleanup task") {
        val cache by createForEachTest { mock<GitRepositoryCache>() }
        val telemetryCaptor by createForEachTest { TestTelemetryCaptor() }
        val logSink by createForEachTest { InMemoryLogSink() }
        val logger by createForEachTest { Logger("Test logger", logSink) }
        val now = ZonedDateTime.of(2020, 5, 13, 6, 30, 0, 0, ZoneOffset.UTC)
        val timeSource: TimeSource = { now }
        var ranOnThread = false

        val threadRunner: ThreadRunner by createForEachTest {
            { block: BackgroundProcess ->
                ranOnThread = true
                block()
            }
        }

        val cleanupTask by createForEachTest { GitRepositoryCacheCleanupTask(cache, telemetryCaptor, logger, threadRunner, timeSource) }

        beforeEachTest { ranOnThread = false }

        fun runWithRepositories(vararg repos: CachedGitRepository) {
            whenever(cache.listAll()).doReturn(repos.toSet())

            cleanupTask.start()
        }

        given("there are no cached repositories") {
            beforeEachTest { runWithRepositories() }

            it("logs a message that there were no repositories to clean up") {
                assertThat(logSink, hasMessage(withLogMessage("No repositories ready for clean up.") and withSeverity(Severity.Info)))
            }

            it("starts a background thread for processing") {
                assertThat(ranOnThread, equalTo(true))
            }
        }

        given("there is a single repository that was last used less than 30 days ago") {
            val repo = CachedGitRepository(GitRepositoryReference("my-repo.git", "v1"), ZonedDateTime.of(2020, 4, 14, 6, 30, 0, 0, ZoneOffset.UTC), null, osIndependentPath("/info.json"))

            beforeEachTest { runWithRepositories(repo) }

            it("logs a message that there were no repositories to clean up") {
                assertThat(logSink, hasMessage(withLogMessage("No repositories ready for clean up.") and withSeverity(Severity.Info)))
            }

            it("starts a background thread for processing") {
                assertThat(ranOnThread, equalTo(true))
            }

            it("does not delete the cached repository") {
                verify(cache, never()).delete(repo)
            }
        }

        given("there is a single repository that was last used more than 30 days ago") {
            val repo = CachedGitRepository(GitRepositoryReference("my-repo.git", "v1"), ZonedDateTime.of(2020, 4, 10, 6, 30, 0, 0, ZoneOffset.UTC), null, osIndependentPath("/info.json"))

            given("deleting the repository succeeds") {
                beforeEachTest { runWithRepositories(repo) }

                it("logs a message before deleting the repository") {
                    assertThat(
                        logSink,
                        hasMessage(
                            withLogMessage("Deleting repository as it has not been used in over 30 days.")
                                and withSeverity(Severity.Info)
                                and withAdditionalData("repo", repo.repo)
                                and withAdditionalData("lastUsed", repo.lastUsed)
                        )
                    )
                }

                it("logs a message after deleting the repository") {
                    assertThat(
                        logSink,
                        hasMessage(
                            withLogMessage("Repository deletion completed.")
                                and withSeverity(Severity.Info)
                                and withAdditionalData("repo", repo.repo)
                        )
                    )
                }

                it("deletes the repository") {
                    verify(cache).delete(repo)
                }
            }

            given("deleting the repository fails") {
                val exception = RuntimeException("Something went wrong.")

                beforeEachTest {
                    whenever(cache.delete(repo)).doThrow(exception)

                    runWithRepositories(repo)
                }

                it("logs a message when deletion fails") {
                    assertThat(
                        logSink,
                        hasMessage(
                            withLogMessage("Repository deletion failed.")
                                and withSeverity(Severity.Warning)
                                and withAdditionalData("repo", repo.repo)
                                and withException(exception)
                        )
                    )
                }

                it("reports the exception in telemetry") {
                    assertThat(telemetryCaptor.allEvents, hasSize(equalTo(1)))

                    val event = telemetryCaptor.allEvents.single()
                    assertThat(event.type, equalTo(CommonEvents.UnhandledException))
                    assertThat(event.attributes[CommonAttributes.ExceptionCaughtAt], equalTo(JsonPrimitive("batect.config.includes.GitRepositoryCacheCleanupTask.delete")))
                    assertThat(event.attributes[CommonAttributes.IsUserFacingException], equalTo(JsonPrimitive(false)))
                }
            }
        }

        given("there are multiple cached repositories, one that was used less than 30 days ago and two that were not used in the last 30 days") {
            val repoToDelete1 = CachedGitRepository(GitRepositoryReference("my-repo.git", "v1"), ZonedDateTime.of(2020, 4, 8, 6, 30, 0, 0, ZoneOffset.UTC), null, osIndependentPath("/info.json"))
            val repoToDelete2 = CachedGitRepository(GitRepositoryReference("my-repo.git", "v2"), ZonedDateTime.of(2020, 4, 10, 6, 30, 0, 0, ZoneOffset.UTC), null, osIndependentPath("/info.json"))
            val repoToKeep = CachedGitRepository(GitRepositoryReference("my-repo.git", "v3"), ZonedDateTime.of(2020, 5, 10, 6, 30, 0, 0, ZoneOffset.UTC), null, osIndependentPath("/info.json"))

            beforeEachTest {
                whenever(cache.listAll()).doReturn(setOf(repoToDelete1, repoToDelete2, repoToKeep))
            }

            given("all repositories are able to be deleted successfully") {
                beforeEachTest { cleanupTask.start() }

                it("deletes both repositories that have not been used in the last 30 days") {
                    verify(cache).delete(repoToDelete1)
                    verify(cache).delete(repoToDelete2)
                }

                it("does not delete the repository that was used less than 30 days ago") {
                    verify(cache, never()).delete(repoToKeep)
                }
            }

            given("deleting the first repository fails") {
                val exception = RuntimeException("Something went wrong.")

                beforeEachTest {
                    var isFirstDeletion = true

                    whenever(cache.delete(any())).thenAnswer {
                        if (isFirstDeletion) {
                            isFirstDeletion = false
                            throw exception
                        }
                    }

                    cleanupTask.start()
                }

                it("still attempts to delete both repositories") {
                    verify(cache).delete(repoToDelete1)
                    verify(cache).delete(repoToDelete2)
                }

                it("logs a message when deletion fails") {
                    assertThat(
                        logSink,
                        hasMessage(
                            withLogMessage("Repository deletion failed.")
                                and withSeverity(Severity.Warning)
                                and withException(exception)
                        )
                    )
                }

                it("reports the exception in telemetry") {
                    assertThat(telemetryCaptor.allEvents, hasSize(equalTo(1)))

                    val event = telemetryCaptor.allEvents.single()
                    assertThat(event.type, equalTo(CommonEvents.UnhandledException))
                    assertThat(event.attributes[CommonAttributes.ExceptionCaughtAt], equalTo(JsonPrimitive("batect.config.includes.GitRepositoryCacheCleanupTask.delete")))
                    assertThat(event.attributes[CommonAttributes.IsUserFacingException], equalTo(JsonPrimitive(false)))
                }
            }
        }
    }
})
