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

package batect.docker

import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object AggregatedImageBuildProgressSpec : Spek({
    describe("aggregated image build progress") {
        describe("converting to a human readable string") {
            given("the image build has no active steps") {
                val event = AggregatedImageBuildProgress(emptySet())

                it("returns a generic description") {
                    assertThat(event.toHumanReadableString(), equalTo("building..."))
                }
            }

            given("the image build has a single active step") {
                given("the step is not downloading") {
                    val event = AggregatedImageBuildProgress(setOf(ActiveImageBuildStep.NotDownloading(1, "step 2 of 3: RUN blah.sh")))

                    it("returns a description of the active step") {
                        assertThat(event.toHumanReadableString(), equalTo("step 2 of 3: RUN blah.sh"))
                    }
                }

                given("the step is downloading") {
                    data class TestCase(val description: String, val step: ActiveImageBuildStep.Downloading, val expected: String)

                    setOf(
                        TestCase(
                            "the download has not started",
                            ActiveImageBuildStep.Downloading(1, "step 2 of 3: FROM postgres:13.0", DownloadOperation.Downloading, 0, 200),
                            "step 2 of 3: FROM postgres:13.0: downloading: 0 B of 200 B (0%)"
                        ),
                        TestCase(
                            "the download is for a file larger than 1 KB",
                            ActiveImageBuildStep.Downloading(1, "step 2 of 3: FROM postgres:13.0", DownloadOperation.Downloading, 1500, 2000),
                            "step 2 of 3: FROM postgres:13.0: downloading: 1.5 KB of 2.0 KB (75%)"
                        ),
                        TestCase(
                            "the download is for a file larger than 1 MB",
                            ActiveImageBuildStep.Downloading(1, "step 2 of 3: FROM postgres:13.0", DownloadOperation.Downloading, 1_750_000, 2_000_000),
                            "step 2 of 3: FROM postgres:13.0: downloading: 1.8 MB of 2.0 MB (88%)"
                        ),
                        TestCase(
                            "the download is for a file larger than 1 GB",
                            ActiveImageBuildStep.Downloading(1, "step 2 of 3: FROM postgres:13.0", DownloadOperation.Downloading, 1_750_000_000, 2_000_000_000),
                            "step 2 of 3: FROM postgres:13.0: downloading: 1.8 GB of 2.0 GB (88%)"
                        ),
                        TestCase(
                            "the download is for a file larger than 1 TB",
                            ActiveImageBuildStep.Downloading(1, "step 2 of 3: FROM postgres:13.0", DownloadOperation.Downloading, 1_750_000_000_000, 2_000_000_000_000),
                            "step 2 of 3: FROM postgres:13.0: downloading: 1.8 TB of 2.0 TB (88%)"
                        ),
                        TestCase(
                            "the download has finished",
                            ActiveImageBuildStep.Downloading(1, "step 2 of 3: FROM postgres:13.0", DownloadOperation.Downloading, 200, 200),
                            "step 2 of 3: FROM postgres:13.0: downloading: 200 B of 200 B (100%)"
                        ),
                        TestCase(
                            "the download is verifying the checksum",
                            ActiveImageBuildStep.Downloading(1, "step 2 of 3: FROM postgres:13.0", DownloadOperation.VerifyingChecksum, 50, 200),
                            "step 2 of 3: FROM postgres:13.0: verifying checksum: 50 B of 200 B (25%)"
                        ),
                        TestCase(
                            "the download is extracting",
                            ActiveImageBuildStep.Downloading(1, "step 2 of 3: FROM postgres:13.0", DownloadOperation.Extracting, 50, 200),
                            "step 2 of 3: FROM postgres:13.0: extracting: 50 B of 200 B (25%)"
                        ),
                        TestCase(
                            "the download is complete",
                            ActiveImageBuildStep.Downloading(1, "step 2 of 3: FROM postgres:13.0", DownloadOperation.DownloadComplete, 200, 200),
                            "step 2 of 3: FROM postgres:13.0: download complete: 200 B of 200 B (100%)"
                        ),
                        TestCase(
                            "the download is for an image pull that is complete",
                            ActiveImageBuildStep.Downloading(1, "step 2 of 3: FROM postgres:13.0", DownloadOperation.PullComplete, 200, 200),
                            "step 2 of 3: FROM postgres:13.0: pull complete: 200 B of 200 B (100%)"
                        ),
                        TestCase(
                            "the download has an invalid total size",
                            ActiveImageBuildStep.Downloading(1, "step 2 of 3: FROM postgres:13.0", DownloadOperation.Downloading, 200, 0),
                            "step 2 of 3: FROM postgres:13.0: downloading: 200 B"
                        ),
                        TestCase(
                            "the download has no total size",
                            ActiveImageBuildStep.Downloading(1, "step 2 of 3: FROM postgres:13.0", DownloadOperation.Downloading, 200, null),
                            "step 2 of 3: FROM postgres:13.0: downloading: 200 B"
                        ),
                        TestCase(
                            "the download has no total size and has not started",
                            ActiveImageBuildStep.Downloading(1, "step 2 of 3: FROM postgres:13.0", DownloadOperation.Downloading, 0, null),
                            "step 2 of 3: FROM postgres:13.0: downloading"
                        )
                    ).forEach { testCase ->
                        given(testCase.description) {
                            val event = AggregatedImageBuildProgress(setOf(testCase.step))

                            it("returns a description of the download") {
                                assertThat(event.toHumanReadableString(), equalTo(testCase.expected))
                            }
                        }
                    }
                }
            }

            given("the image build has two active steps") {
                val event = AggregatedImageBuildProgress(
                    setOf(
                        ActiveImageBuildStep.NotDownloading(1, "step 2 of 30: RUN blah.sh"),
                        ActiveImageBuildStep.NotDownloading(4, "step 5 of 30: RUN foo.sh")
                    )
                )

                it("returns a description of the earliest step and indicates another step is running") {
                    assertThat(event.toHumanReadableString(), equalTo("step 2 of 30: RUN blah.sh (+1 other step running)"))
                }
            }

            given("the image build has three active steps") {
                val event = AggregatedImageBuildProgress(
                    setOf(
                        ActiveImageBuildStep.NotDownloading(1, "step 2 of 30: RUN blah.sh"),
                        ActiveImageBuildStep.NotDownloading(4, "step 5 of 30: RUN foo.sh"),
                        ActiveImageBuildStep.NotDownloading(9, "step 10 of 30: RUN bar.sh")
                    )
                )

                it("returns a description of the earliest step and indicates other steps are running") {
                    assertThat(event.toHumanReadableString(), equalTo("step 2 of 30: RUN blah.sh (+2 other steps running)"))
                }
            }
        }
    }
})
