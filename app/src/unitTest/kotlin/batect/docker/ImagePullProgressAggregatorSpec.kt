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

import batect.dockerclient.ImagePullProgressDetail
import batect.dockerclient.ImagePullProgressUpdate
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runNullableForEachTest
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ImagePullProgressAggregatorSpec : Spek({
    describe("a Docker image pull progress aggregator") {
        val aggregator by createForEachTest { ImagePullProgressAggregator() }

        // This is the rough idea:
        // - we should show nothing if we have no layer information at all
        // - we should show 'downloading' while any layer is downloading
        // - we should show 'verifying checksum' if all layers are verifying checksum or later and one or more is verifying a checksum
        // - we should show 'download complete' only if all layers are download complete
        // - we should show 'extracting' if all layers are download complete or later and one or more is extracting
        // - we should show 'pull complete' only if all layers are pull complete OR if all layers are download complete or later and at least one is pull complete and none are extracting

        mapOf(
            "an 'image pull starting' event" to ImagePullProgressUpdate("Pulling from library/node", null, "10-stretch"),
            "a 'layer already exists' event" to ImagePullProgressUpdate("Already exists", null, "55cbf04beb70"),
            "a 'layer pull starting' event" to ImagePullProgressUpdate("Pulling fs layer", null, "d6cd23cd1a2c"),
            "a 'waiting to pull layer' event" to ImagePullProgressUpdate("Waiting", null, "4c60885e4f94"),
            "an 'image digest' event" to ImagePullProgressUpdate("Digest: sha256:e6b798f4eeb4e6334d195cdeabc18d07dc5158aa88ad5d83670462852b431a71", null, ""),
            "a 'downloaded newer image' event" to ImagePullProgressUpdate("Status: Downloaded newer image for node:10-stretch", null, ""),
            "an 'image up to date' event" to ImagePullProgressUpdate("Status: Image is up to date for node:10-stretch", null, ""),
        ).forEach { (description, raw) ->
            given(description) {
                on("processing the event") {
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("does not return an update") {
                        assertThat(progressUpdate, absent())
                    }
                }
            }
        }

        given("the image is not being pulled in the context of a BuildKit image build") {
            given("a single 'layer downloading' event") {
                val raw = ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(329, 4159), "d6cd23cd1a2c")

                on("processing the event") {
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("returns an appropriate progress update") {
                        assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Downloading, 329, 4159)))
                    }
                }
            }

            given("a single 'layer extracting' event") {
                val raw = ImagePullProgressUpdate("Extracting", ImagePullProgressDetail(329, 4159), "d6cd23cd1a2c")

                on("processing the event") {
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("returns an appropriate progress update") {
                        assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Extracting, 329, 4159)))
                    }
                }
            }

            given("a single 'verifying checksum' event without any previous events for that layer") {
                val raw = ImagePullProgressUpdate("Verifying Checksum", null, "d6cd23cd1a2c")

                on("processing the event") {
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("returns an appropriate progress update") {
                        assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.VerifyingChecksum, 0, 0)))
                    }
                }
            }

            given("a single 'layer downloading' event has been processed") {
                beforeEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(329, 4159), "d6cd23cd1a2c"))
                }

                on("processing another 'layer downloading' event for the same layer") {
                    val raw = ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(900, 4159), "d6cd23cd1a2c")
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("returns an appropriate progress update") {
                        assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Downloading, 900, 4159)))
                    }
                }

                on("processing another 'layer downloading' event for the same layer that does not result in updated information") {
                    val raw = ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(329, 4159), "d6cd23cd1a2c")
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("does not return an update") {
                        assertThat(progressUpdate, absent())
                    }
                }

                on("processing a 'verifying checksum' event for the same layer") {
                    // Note that the 'status' field for a 'verifying checksum' event has the C capitalised, while
                    // every other kind of event uses lowercase for later words in the description.
                    val raw = ImagePullProgressUpdate("Verifying Checksum", null, "d6cd23cd1a2c")
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("returns an appropriate progress update") {
                        assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.VerifyingChecksum, 0, 4159)))
                    }
                }

                on("processing a 'download complete' event for the same layer") {
                    val raw = ImagePullProgressUpdate("Download complete", null, "d6cd23cd1a2c")
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("returns an appropriate progress update") {
                        assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.DownloadComplete, 4159, 4159)))
                    }
                }

                on("processing an 'extracting' event for the same layer") {
                    val raw = ImagePullProgressUpdate("Extracting", ImagePullProgressDetail(1000, 4159), "d6cd23cd1a2c")
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("returns an appropriate progress update") {
                        assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Extracting, 1000, 4159)))
                    }
                }

                on("processing a 'pull complete' event for the same layer") {
                    val raw = ImagePullProgressUpdate("Pull complete", null, "d6cd23cd1a2c")
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("returns an appropriate progress update") {
                        assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.PullComplete, 4159, 4159)))
                    }
                }

                on("processing a 'layer downloading' event for another layer") {
                    val raw = ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(900, 7000), "b59856e9f0ab")
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("returns a progress update combining the state of both layers") {
                        assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Downloading, 329L + 900, 4159L + 7000)))
                    }
                }

                given("a 'layer downloading' event for another layer has been processed") {
                    beforeEachTest {
                        aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(900, 7000), "b59856e9f0ab"))
                    }

                    mapOf(
                        "verifying checksum" to ImagePullProgressUpdate("Verifying Checksum", null, "b59856e9f0ab"),
                        "download complete" to ImagePullProgressUpdate("Download complete", null, "b59856e9f0ab"),
                        "extracting" to ImagePullProgressUpdate("Extracting", ImagePullProgressDetail(1000, 7000), "b59856e9f0ab"),
                        "pull complete" to ImagePullProgressUpdate("Pull complete", null, "b59856e9f0ab"),
                    ).forEach { (eventType, raw) ->
                        on("processing a '$eventType' event for that other layer") {
                            val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                            it("returns a progress update combining the state of both layers") {
                                assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Downloading, 329L + 7000, 4159L + 7000)))
                            }
                        }
                    }
                }

                given("a 'pull complete' event has been processed") {
                    beforeEachTest {
                        aggregator.processProgressUpdate(ImagePullProgressUpdate("Pull complete", null, "d6cd23cd1a2c"))
                    }

                    on("processing a 'layer downloading' event for another layer") {
                        val raw = ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(900, 7000), "b59856e9f0ab")
                        val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                        it("returns a progress update combining the state of both layers") {
                            assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Downloading, 4159L + 900, 4159L + 7000)))
                        }
                    }
                }

                given("a 'download complete' event has been processed") {
                    beforeEachTest {
                        aggregator.processProgressUpdate(ImagePullProgressUpdate("Download complete", null, "d6cd23cd1a2c"))
                    }

                    on("processing an 'extracting' event for another layer") {
                        val raw = ImagePullProgressUpdate("Extracting", ImagePullProgressDetail(900, 7000), "b59856e9f0ab")
                        val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                        it("returns a progress update combining the state of both layers") {
                            assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Extracting, 900, 4159L + 7000)))
                        }
                    }
                }
            }

            given("a single 'verifying checksum' event has been processed") {
                beforeEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Verifying Checksum", null, "d6cd23cd1a2c"))
                }

                on("processing another 'verifying checksum' event for the same layer") {
                    val raw = ImagePullProgressUpdate("Verifying Checksum", null, "d6cd23cd1a2c")
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("does not return an update") {
                        assertThat(progressUpdate, absent())
                    }
                }

                on("processing a 'layer downloading' event for the same layer") {
                    val raw = ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(900, 4159), "d6cd23cd1a2c")
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("returns an appropriate progress update") {
                        assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Downloading, 900, 4159)))
                    }
                }
            }

            given("all layers are downloading") {
                val progressUpdate by runNullableForEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(300, 4000), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(600, 3000), "55cbf04beb70"))
                }

                it("returns a progress update that indicates the image is downloading") {
                    assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Downloading, 900, 7000)))
                }
            }

            given("some layers are downloading and some have finished downloading") {
                val progressUpdate by runNullableForEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(300, 4000), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Download complete", null, "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(600, 3000), "55cbf04beb70"))
                }

                it("returns a progress update that indicates the image is downloading") {
                    assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Downloading, 4600, 7000)))
                }
            }

            given("some layers are downloading and some are extracting") {
                val progressUpdate by runNullableForEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(300, 4000), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Extracting", ImagePullProgressDetail(600, 3000), "55cbf04beb70"))
                }

                it("returns a progress update that indicates the image is downloading") {
                    assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Downloading, 3300, 7000)))
                }
            }

            given("some layers are downloading, some are verifying checksums and some have finished downloading") {
                val progressUpdate by runNullableForEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(50, 9000), "4c60885e4f94"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(300, 4000), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Verifying Checksum", null, "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(600, 3000), "55cbf04beb70"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Download complete", null, "55cbf04beb70"))
                }

                it("returns a progress update that indicates the image is downloading") {
                    assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Downloading, 7050, 16000)))
                }
            }

            given("some layers are verifying checksums and some have finished downloading") {
                val progressUpdate by runNullableForEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(300, 4000), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Verifying Checksum", null, "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(600, 3000), "55cbf04beb70"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Download complete", null, "55cbf04beb70"))
                }

                it("returns a progress update that indicates the checksum is being verified") {
                    assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.VerifyingChecksum, 3000, 7000)))
                }
            }

            given("some layers have finished downloading and some are extracting") {
                val progressUpdate by runNullableForEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(300, 4000), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Extracting", ImagePullProgressDetail(700, 4000), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(600, 3000), "55cbf04beb70"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Download complete", null, "55cbf04beb70"))
                }

                it("returns a progress update that indicates the image is extracting") {
                    assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Extracting, 700, 7000)))
                }
            }

            given("all layers have finished downloading") {
                val progressUpdate by runNullableForEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(300, 4000), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Download complete", null, "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(600, 3000), "55cbf04beb70"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Download complete", null, "55cbf04beb70"))
                }

                it("returns a progress update that indicates the image has finished downloading") {
                    assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.DownloadComplete, 7000, 7000)))
                }
            }

            given("some layers have finished downloading and some have finished pulling") {
                val progressUpdate by runNullableForEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(300, 4000), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Download complete", null, "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(600, 3000), "55cbf04beb70"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Pull complete", null, "55cbf04beb70"))
                }

                it("returns a progress update that indicates the image has partly finished pulling") {
                    assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.PullComplete, 3000, 7000)))
                }
            }

            given("some layers are extracting and some have finished pulling") {
                val progressUpdate by runNullableForEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(300, 4000), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Extracting", ImagePullProgressDetail(700, 4000), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(600, 3000), "55cbf04beb70"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Pull complete", null, "55cbf04beb70"))
                }

                it("returns a progress update that indicates the image is extracting") {
                    assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Extracting, 3700, 7000)))
                }
            }

            given("all layers have finished pulling") {
                val progressUpdate by runNullableForEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(300, 4000), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Pull complete", null, "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Downloading", ImagePullProgressDetail(600, 3000), "55cbf04beb70"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("Pull complete", null, "55cbf04beb70"))
                }

                it("returns a progress update that indicates the image has finished pulling") {
                    assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.PullComplete, 7000, 7000)))
                }
            }
        }

        given("the image is being pulled in the context of a BuildKit image build") {
            // The image pull updates emitted during a BuildKit image build are a bit different to the legacy builder:
            // - The names used for each step are different ('downloading' instead of 'Downloading', 'extract' instead of 'Extracting' etc.)
            // - There is no 'verifying checksum' step.
            // - There is no 'pull complete' step.
            // - No progress information is reported during extraction. Instead, one layer is extracted at a time and when the next starts,
            //   it is safe to assume the previous layer has completed.

            given("a single 'layer downloading' event") {
                val raw = ImagePullProgressUpdate("downloading", ImagePullProgressDetail(329, 4159), "d6cd23cd1a2c")

                on("processing the event") {
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("returns an appropriate progress update") {
                        assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Downloading, 329, 4159)))
                    }
                }
            }

            given("a single 'layer extracting' event") {
                val raw = ImagePullProgressUpdate("extract", ImagePullProgressDetail(0, 0), "d6cd23cd1a2c")

                on("processing the event") {
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("returns an appropriate progress update") {
                        assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Extracting, 0, 0)))
                    }
                }
            }

            given("a single 'layer downloading' event has been processed") {
                beforeEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("downloading", ImagePullProgressDetail(329, 4159), "d6cd23cd1a2c"))
                }

                on("processing another 'layer downloading' event for the same layer") {
                    val raw = ImagePullProgressUpdate("downloading", ImagePullProgressDetail(900, 4159), "d6cd23cd1a2c")
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("returns an appropriate progress update") {
                        assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Downloading, 900, 4159)))
                    }
                }

                on("processing another 'layer downloading' event for the same layer that does not result in updated information") {
                    val raw = ImagePullProgressUpdate("downloading", ImagePullProgressDetail(329, 4159), "d6cd23cd1a2c")
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("does not return an update") {
                        assertThat(progressUpdate, absent())
                    }
                }

                on("processing a 'download complete' event for the same layer") {
                    val raw = ImagePullProgressUpdate("done", null, "d6cd23cd1a2c")
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("returns an appropriate progress update") {
                        assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.DownloadComplete, 4159, 4159)))
                    }
                }

                on("processing an 'extracting' event for the same layer") {
                    val raw = ImagePullProgressUpdate("extract", ImagePullProgressDetail(0, 0), "d6cd23cd1a2c")
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("returns an appropriate progress update") {
                        assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Extracting, 0, 4159)))
                    }
                }

                on("processing a 'layer downloading' event for another layer") {
                    val raw = ImagePullProgressUpdate("downloading", ImagePullProgressDetail(900, 7000), "b59856e9f0ab")
                    val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                    it("returns a progress update combining the state of both layers") {
                        assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Downloading, 329L + 900, 4159L + 7000)))
                    }
                }

                given("a 'layer downloading' event for another layer has been processed") {
                    beforeEachTest {
                        aggregator.processProgressUpdate(ImagePullProgressUpdate("downloading", ImagePullProgressDetail(900, 7000), "b59856e9f0ab"))
                    }

                    mapOf(
                        "download complete" to ImagePullProgressUpdate("done", null, "b59856e9f0ab"),
                        "extracting" to ImagePullProgressUpdate("extract", ImagePullProgressDetail(0, 0), "b59856e9f0ab"),
                    ).forEach { (eventType, raw) ->
                        on("processing a '$eventType' event for that other layer") {
                            val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                            it("returns a progress update combining the state of both layers") {
                                assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Downloading, 329L + 7000, 4159L + 7000)))
                            }
                        }
                    }
                }

                given("a 'download complete' event has been processed") {
                    beforeEachTest {
                        aggregator.processProgressUpdate(ImagePullProgressUpdate("done", null, "d6cd23cd1a2c"))
                    }

                    on("processing an 'extracting' event for another layer") {
                        val raw = ImagePullProgressUpdate("extract", ImagePullProgressDetail(0, 0), "b59856e9f0ab")
                        val progressUpdate by runNullableForEachTest { aggregator.processProgressUpdate(raw) }

                        it("returns a progress update combining the state of both layers") {
                            assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Extracting, 0, 4159)))
                        }
                    }
                }
            }

            given("all layers are downloading") {
                val progressUpdate by runNullableForEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("downloading", ImagePullProgressDetail(300, 4000), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("downloading", ImagePullProgressDetail(600, 3000), "55cbf04beb70"))
                }

                it("returns a progress update that indicates the image is downloading") {
                    assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Downloading, 900, 7000)))
                }
            }

            given("some layers are downloading and some have finished downloading") {
                val progressUpdate by runNullableForEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("downloading", ImagePullProgressDetail(300, 4000), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("done", null, "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("downloading", ImagePullProgressDetail(600, 3000), "55cbf04beb70"))
                }

                it("returns a progress update that indicates the image is downloading") {
                    assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Downloading, 4600, 7000)))
                }
            }

            given("some layers are downloading and some are extracting") {
                val progressUpdate by runNullableForEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("downloading", ImagePullProgressDetail(300, 4000), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("downloading", ImagePullProgressDetail(0, 3000), "55cbf04beb70"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("extract", ImagePullProgressDetail(0, 0), "55cbf04beb70"))
                }

                it("returns a progress update that indicates the image is downloading") {
                    assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Downloading, 3300, 7000)))
                }
            }

            given("some layers have finished downloading and some are extracting") {
                val progressUpdate by runNullableForEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("downloading", ImagePullProgressDetail(300, 4000), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("extract", ImagePullProgressDetail(0, 0), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("downloading", ImagePullProgressDetail(600, 3000), "55cbf04beb70"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("done", null, "55cbf04beb70"))
                }

                it("returns a progress update that indicates the image is extracting") {
                    assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Extracting, 0, 7000)))
                }
            }

            given("all layers have finished downloading") {
                val progressUpdate by runNullableForEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("downloading", ImagePullProgressDetail(300, 4000), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("done", null, "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("downloading", ImagePullProgressDetail(600, 3000), "55cbf04beb70"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("done", null, "55cbf04beb70"))
                }

                it("returns a progress update that indicates the image has finished downloading") {
                    assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.DownloadComplete, 7000, 7000)))
                }
            }

            given("some layers have finished extracting and another is still extracting") {
                val progressUpdate by runNullableForEachTest {
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("downloading", ImagePullProgressDetail(300, 4000), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("extract", ImagePullProgressDetail(0, 0), "d6cd23cd1a2c"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("downloading", ImagePullProgressDetail(600, 3000), "55cbf04beb70"))
                    aggregator.processProgressUpdate(ImagePullProgressUpdate("extract", ImagePullProgressDetail(0, 0), "55cbf04beb70"))
                }

                it("returns a progress update that indicates the image is extracting") {
                    assertThat(progressUpdate, equalTo(AggregatedImagePullProgress(DownloadOperation.Extracting, 4000, 7000)))
                }
            }
        }
    }
})
