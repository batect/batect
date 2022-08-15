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

package batect.docker

import batect.dockerclient.BuildComplete
import batect.dockerclient.BuildFailed
import batect.dockerclient.ImagePullProgressDetail
import batect.dockerclient.ImagePullProgressUpdate
import batect.dockerclient.ImageReference
import batect.dockerclient.StepDownloadProgressUpdate
import batect.dockerclient.StepFinished
import batect.dockerclient.StepOutput
import batect.dockerclient.StepPullProgressUpdate
import batect.dockerclient.StepStarting
import batect.testutils.createForEachTest
import batect.testutils.on
import batect.testutils.runNullableForEachTest
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ImageBuildProgressAggregatorSpec : Spek({
    describe("an image build progress aggregator") {
        on("receiving output for a step") {
            val aggregator by createForEachTest { ImageBuildProgressAggregator() }
            val nextUpdate by runNullableForEachTest { aggregator.processProgressUpdate(StepOutput(1, "some output")) }

            it("does not emit a new update") {
                assertThat(nextUpdate, absent())
            }
        }

        on("the build failing") {
            val aggregator by createForEachTest { ImageBuildProgressAggregator() }
            val nextUpdate by runNullableForEachTest { aggregator.processProgressUpdate(BuildFailed("some error")) }

            it("does not emit a new update") {
                assertThat(nextUpdate, absent())
            }
        }

        on("the build completing") {
            val aggregator by createForEachTest { ImageBuildProgressAggregator() }
            val nextUpdate by runNullableForEachTest { aggregator.processProgressUpdate(BuildComplete(ImageReference("the-image-id"))) }

            it("does not emit a new update") {
                assertThat(nextUpdate, absent())
            }
        }

        on("a step starting") {
            val aggregator by createForEachTest { ImageBuildProgressAggregator() }
            val nextUpdate by runNullableForEachTest { aggregator.processProgressUpdate(StepStarting(1, "FROM postgres:13.0")) }

            it("emits a new update with that step") {
                assertThat(
                    nextUpdate,
                    equalTo(
                        AggregatedImageBuildProgress(setOf(ActiveImageBuildStep.NotDownloading(1, "FROM postgres:13.0")))
                    )
                )
            }
        }

        on("a step finishing with no other steps still running") {
            val aggregator by createForEachTest { ImageBuildProgressAggregator() }
            val nextUpdate by runNullableForEachTest {
                aggregator.processProgressUpdate(StepStarting(1, "FROM postgres:13.0"))
                aggregator.processProgressUpdate(StepFinished(1))
            }

            it("emits a new update with no steps running") {
                assertThat(nextUpdate, equalTo(AggregatedImageBuildProgress(emptySet())))
            }
        }

        // This sometimes happens when building with BuildKit.
        on("a step restarting") {
            val aggregator by createForEachTest { ImageBuildProgressAggregator() }
            val nextUpdate by runNullableForEachTest {
                aggregator.processProgressUpdate(StepStarting(1, "FROM postgres:13.0"))
                aggregator.processProgressUpdate(StepFinished(1))
                aggregator.processProgressUpdate(StepStarting(1, "FROM postgres:13.0"))
            }

            it("emits a new update with no steps running") {
                assertThat(
                    nextUpdate,
                    equalTo(
                        AggregatedImageBuildProgress(setOf(ActiveImageBuildStep.NotDownloading(1, "FROM postgres:13.0")))
                    )
                )
            }
        }

        on("multiple steps starting") {
            val aggregator by createForEachTest { ImageBuildProgressAggregator() }

            val nextUpdate by runNullableForEachTest {
                aggregator.processProgressUpdate(StepStarting(1, "FROM postgres:13.0"))
                aggregator.processProgressUpdate(StepStarting(2, "HEALTHCHECK --interval=0.1s CMD echo -n \\\"Hello from the healthcheck\\\""))
            }

            it("emits a new update with both steps") {
                assertThat(
                    nextUpdate,
                    equalTo(
                        AggregatedImageBuildProgress(
                            setOf(
                                ActiveImageBuildStep.NotDownloading(1, "FROM postgres:13.0"),
                                ActiveImageBuildStep.NotDownloading(2, "HEALTHCHECK --interval=0.1s CMD echo -n \\\"Hello from the healthcheck\\\"")
                            )
                        )
                    )
                )
            }
        }

        on("a step finishing with another step still running") {
            val aggregator by createForEachTest { ImageBuildProgressAggregator() }
            val nextUpdate by runNullableForEachTest {
                aggregator.processProgressUpdate(StepStarting(1, "FROM postgres:13.0"))
                aggregator.processProgressUpdate(StepStarting(2, "FROM postgres:14.0"))
                aggregator.processProgressUpdate(StepFinished(1))
            }

            it("emits a new update with only the remaining step") {
                assertThat(
                    nextUpdate,
                    equalTo(
                        AggregatedImageBuildProgress(setOf(ActiveImageBuildStep.NotDownloading(2, "FROM postgres:14.0")))
                    )
                )
            }
        }

        on("a single step reporting file download progress") {
            val aggregator by createForEachTest { ImageBuildProgressAggregator() }

            val nextUpdate by runNullableForEachTest {
                aggregator.processProgressUpdate(StepStarting(1, "ADD http://httpbin.org/get test.txt"))
                aggregator.processProgressUpdate(StepDownloadProgressUpdate(1, 329, 4159))
            }

            it("emits a new update with download progress information") {
                assertThat(
                    nextUpdate,
                    equalTo(
                        AggregatedImageBuildProgress(
                            setOf(
                                ActiveImageBuildStep.Downloading(1, "ADD http://httpbin.org/get test.txt", DownloadOperation.Downloading, 329, 4159)
                            )
                        )
                    )
                )
            }
        }

        on("a single step reporting file download progress with an unknown total size") {
            val aggregator by createForEachTest { ImageBuildProgressAggregator() }

            val nextUpdate by runNullableForEachTest {
                aggregator.processProgressUpdate(StepStarting(1, "ADD http://httpbin.org/get test.txt"))
                aggregator.processProgressUpdate(StepDownloadProgressUpdate(1, 329, -1))
            }

            it("emits a new update with download progress information") {
                assertThat(
                    nextUpdate,
                    equalTo(
                        AggregatedImageBuildProgress(
                            setOf(
                                ActiveImageBuildStep.Downloading(1, "ADD http://httpbin.org/get test.txt", DownloadOperation.Downloading, 329, null)
                            )
                        )
                    )
                )
            }
        }

        on("multiple steps reporting file download progress") {
            val aggregator by createForEachTest { ImageBuildProgressAggregator() }

            val nextUpdate by runNullableForEachTest {
                aggregator.processProgressUpdate(StepStarting(1, "ADD http://httpbin.org/get test.txt"))
                aggregator.processProgressUpdate(StepDownloadProgressUpdate(1, 329, 4159))
                aggregator.processProgressUpdate(StepStarting(2, "ADD http://httpbin.org/get test2.txt"))
                aggregator.processProgressUpdate(StepDownloadProgressUpdate(2, 100, 2059))
            }

            it("emits a new update with download progress information for each running step") {
                assertThat(
                    nextUpdate,
                    equalTo(
                        AggregatedImageBuildProgress(
                            setOf(
                                ActiveImageBuildStep.Downloading(1, "ADD http://httpbin.org/get test.txt", DownloadOperation.Downloading, 329, 4159),
                                ActiveImageBuildStep.Downloading(2, "ADD http://httpbin.org/get test2.txt", DownloadOperation.Downloading, 100, 2059)
                            )
                        )
                    )
                )
            }
        }

        on("a single step reporting a single image pull progress update") {
            val aggregator by createForEachTest { ImageBuildProgressAggregator() }

            val nextUpdate by runNullableForEachTest {
                aggregator.processProgressUpdate(StepStarting(1, "FROM postgres:13.0"))
                aggregator.processProgressUpdate(StepPullProgressUpdate(1, ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(123, 456), "abc123")))
            }

            it("emits a new update with pull progress information") {
                assertThat(
                    nextUpdate,
                    equalTo(
                        AggregatedImageBuildProgress(
                            setOf(
                                ActiveImageBuildStep.Downloading(1, "FROM postgres:13.0", DownloadOperation.Downloading, 123, 456)
                            )
                        )
                    )
                )
            }
        }

        // There are further tests for this in ImagePullProgressAggregator.
        on("a single step reporting multiple pull progress updates") {
            val aggregator by createForEachTest { ImageBuildProgressAggregator() }

            val nextUpdate by runNullableForEachTest {
                aggregator.processProgressUpdate(StepStarting(1, "FROM postgres:13.0"))
                aggregator.processProgressUpdate(StepPullProgressUpdate(1, ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(123, 456), "abc123")))
                aggregator.processProgressUpdate(StepPullProgressUpdate(1, ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(789, 1000), "def456")))
            }

            it("emits a new update with aggregated pull progress information") {
                assertThat(
                    nextUpdate,
                    equalTo(
                        AggregatedImageBuildProgress(
                            setOf(
                                ActiveImageBuildStep.Downloading(1, "FROM postgres:13.0", DownloadOperation.Downloading, 123 + 789, 456 + 1000)
                            )
                        )
                    )
                )
            }
        }

        on("multiple steps each reporting a single image pull progress update") {
            val aggregator by createForEachTest { ImageBuildProgressAggregator() }

            val nextUpdate by runNullableForEachTest {
                aggregator.processProgressUpdate(StepStarting(1, "FROM postgres:13.0"))
                aggregator.processProgressUpdate(StepPullProgressUpdate(1, ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(123, 456), "abc123")))
                aggregator.processProgressUpdate(StepStarting(2, "FROM postgres:14.0"))
                aggregator.processProgressUpdate(StepPullProgressUpdate(2, ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(789, 1000), "abc123")))
            }

            it("emits a new update with pull progress information for all steps") {
                assertThat(
                    nextUpdate,
                    equalTo(
                        AggregatedImageBuildProgress(
                            setOf(
                                ActiveImageBuildStep.Downloading(1, "FROM postgres:13.0", DownloadOperation.Downloading, 123, 456),
                                ActiveImageBuildStep.Downloading(2, "FROM postgres:14.0", DownloadOperation.Downloading, 789, 1000)
                            )
                        )
                    )
                )
            }
        }
    }
})
