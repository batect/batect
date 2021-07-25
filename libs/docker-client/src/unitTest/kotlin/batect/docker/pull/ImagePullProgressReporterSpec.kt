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

package batect.docker.pull

import batect.docker.DownloadOperation
import batect.docker.Json
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runNullableForEachTest
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import kotlinx.serialization.json.jsonObject
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ImagePullProgressReporterSpec : Spek({
    describe("a Docker image pull progress reporter") {
        val reporter by createForEachTest { ImagePullProgressReporter() }

        // This is the rough idea:
        // - we should show nothing if we have no layer information at all
        // - we should show 'downloading' while any layer is downloading
        // - we should show 'verifying checksum' if all layers are verifying checksum or later and one or more is verifying a checksum
        // - we should show 'download complete' only if all layers are download complete
        // - we should show 'extracting' if all layers are download complete or later and one or more is extracting
        // - we should show 'pull complete' only if all layers are pull complete OR if all layers are download complete or later and at least one is pull complete and none are extracting

        mapOf(
            "an empty event" to "{}",
            "an 'image pull starting' event" to """{"status":"Pulling from library/node","id":"10-stretch"}""",
            "a 'layer already exists' event" to """{"status":"Already exists","progressDetail":{},"id":"55cbf04beb70"}""",
            "a 'layer pull starting' event" to """{"status":"Pulling fs layer","progressDetail":{},"id":"d6cd23cd1a2c"}""",
            "a 'waiting to pull layer' event" to """{"status":"Waiting","progressDetail":{},"id":"4c60885e4f94"}""",
            "an 'image digest' event" to """{"status":"Digest: sha256:e6b798f4eeb4e6334d195cdeabc18d07dc5158aa88ad5d83670462852b431a71"}""",
            "a 'downloaded newer image' event" to """{"status":"Status: Downloaded newer image for node:10-stretch"}""",
            "an 'image up to date' event" to """{"status":"Status: Image is up to date for node:10-stretch"}"""
        ).forEach { (description, json) ->
            given(description) {
                on("processing the event") {
                    val progressUpdate by runNullableForEachTest { reporter.processRawProgressUpdate(json) }

                    it("does not return an update") {
                        assertThat(progressUpdate, absent())
                    }
                }
            }
        }

        given("a single 'layer downloading' event") {
            val json = """{"status":"Downloading","progressDetail":{"current":329,"total":4159},"id":"d6cd23cd1a2c"}"""

            on("processing the event") {
                val progressUpdate by runNullableForEachTest { reporter.processRawProgressUpdate(json) }

                it("returns an appropriate progress update") {
                    assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.Downloading, 329, 4159)))
                }
            }
        }

        given("a single 'layer extracting' event") {
            val json = """{"status":"Extracting","progressDetail":{"current":329,"total":4159},"id":"d6cd23cd1a2c"}"""

            on("processing the event") {
                val progressUpdate by runNullableForEachTest { reporter.processRawProgressUpdate(json) }

                it("returns an appropriate progress update") {
                    assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.Extracting, 329, 4159)))
                }
            }
        }

        given("a single 'verifying checksum' event without any previous events for that layer") {
            val json = """{"status":"Verifying Checksum","progressDetail":{},"id":"d6cd23cd1a2c"}"""

            on("processing the event") {
                val progressUpdate by runNullableForEachTest { reporter.processRawProgressUpdate(json) }

                it("returns an appropriate progress update") {
                    assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.VerifyingChecksum, 0, 0)))
                }
            }
        }

        given("a single 'layer downloading' event has been processed") {
            beforeEachTest {
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":329,"total":4159},"id":"d6cd23cd1a2c"}""")
            }

            on("processing another 'layer downloading' event for the same layer") {
                val json = """{"status":"Downloading","progressDetail":{"current":900,"total":4159},"id":"d6cd23cd1a2c"}"""
                val progressUpdate by runNullableForEachTest { reporter.processRawProgressUpdate(json) }

                it("returns an appropriate progress update") {
                    assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.Downloading, 900, 4159)))
                }
            }

            on("processing another 'layer downloading' event for the same layer that does not result in updated information") {
                val json = """{"status":"Downloading","progressDetail":{"current":329,"total":4159},"id":"d6cd23cd1a2c"}"""
                val progressUpdate by runNullableForEachTest { reporter.processRawProgressUpdate(json) }

                it("does not return an update") {
                    assertThat(progressUpdate, absent())
                }
            }

            on("processing a 'verifying checksum' event for the same layer") {
                // Note that the 'status' field for a 'verifying checksum' event has the C capitalised, while
                // every other kind of event uses lowercase for later words in the description.
                val json = """{"status":"Verifying Checksum","progressDetail":{},"id":"d6cd23cd1a2c"}"""
                val progressUpdate by runNullableForEachTest { reporter.processRawProgressUpdate(json) }

                it("returns an appropriate progress update") {
                    assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.VerifyingChecksum, 0, 4159)))
                }
            }

            on("processing a 'download complete' event for the same layer") {
                val json = """{"status":"Download complete","progressDetail":{},"id":"d6cd23cd1a2c"}"""
                val progressUpdate by runNullableForEachTest { reporter.processRawProgressUpdate(json) }

                it("returns an appropriate progress update") {
                    assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.DownloadComplete, 4159, 4159)))
                }
            }

            on("processing an 'extracting' event for the same layer") {
                val json = """{"status":"Extracting","progressDetail":{"current":1000,"total":4159},"id":"d6cd23cd1a2c"}"""
                val progressUpdate by runNullableForEachTest { reporter.processRawProgressUpdate(json) }

                it("returns an appropriate progress update") {
                    assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.Extracting, 1000, 4159)))
                }
            }

            on("processing a 'pull complete' event for the same layer") {
                val json = """{"status":"Pull complete","progressDetail":{},"id":"d6cd23cd1a2c"}"""
                val progressUpdate by runNullableForEachTest { reporter.processRawProgressUpdate(json) }

                it("returns an appropriate progress update") {
                    assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.PullComplete, 4159, 4159)))
                }
            }

            on("processing a 'layer downloading' event for another layer") {
                val json = """{"status":"Downloading","progressDetail":{"current":900,"total":7000},"id":"b59856e9f0ab"}"""
                val progressUpdate by runNullableForEachTest { reporter.processRawProgressUpdate(json) }

                it("returns a progress update combining the state of both layers") {
                    assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.Downloading, 329L + 900, 4159L + 7000)))
                }
            }

            given("a 'layer downloading' event for another layer has been processed") {
                beforeEachTest {
                    reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":900,"total":7000},"id":"b59856e9f0ab"}""")
                }

                mapOf(
                    "verifying checksum" to """{"status":"Verifying Checksum","progressDetail":{},"id":"b59856e9f0ab"}""",
                    "download complete" to """{"status":"Download complete","progressDetail":{},"id":"b59856e9f0ab"}""",
                    "extracting" to """{"status":"Extracting","progressDetail":{"current":1000,"total":7000},"id":"b59856e9f0ab"}""",
                    "pull complete" to """{"status":"Pull complete","progressDetail":{},"id":"b59856e9f0ab"}"""
                ).forEach { (eventType, json) ->
                    on("processing a '$eventType' event for that other layer") {
                        val progressUpdate by runNullableForEachTest { reporter.processRawProgressUpdate(json) }

                        it("returns a progress update combining the state of both layers") {
                            assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.Downloading, 329L + 7000, 4159L + 7000)))
                        }
                    }
                }
            }

            given("a 'pull complete' event has been processed") {
                beforeEachTest {
                    reporter.processRawProgressUpdate("""{"status":"Pull complete","progressDetail":{},"id":"d6cd23cd1a2c"}""")
                }

                on("processing a 'layer downloading' event for another layer") {
                    val json = """{"status":"Downloading","progressDetail":{"current":900,"total":7000},"id":"b59856e9f0ab"}"""
                    val progressUpdate by runNullableForEachTest { reporter.processRawProgressUpdate(json) }

                    it("returns a progress update combining the state of both layers") {
                        assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.Downloading, 4159L + 900, 4159L + 7000)))
                    }
                }
            }

            given("a 'download complete' event has been processed") {
                beforeEachTest {
                    reporter.processRawProgressUpdate("""{"status":"Download complete","progressDetail":{},"id":"d6cd23cd1a2c"}""")
                }

                on("processing an 'extracting' event for another layer") {
                    val json = """{"status":"Extracting","progressDetail":{"current":900,"total":7000},"id":"b59856e9f0ab"}"""
                    val progressUpdate by runNullableForEachTest { reporter.processRawProgressUpdate(json) }

                    it("returns a progress update combining the state of both layers") {
                        assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.Extracting, 900, 4159L + 7000)))
                    }
                }
            }
        }

        given("a single 'verifying checksum' event has been processed") {
            beforeEachTest {
                reporter.processRawProgressUpdate("""{"status":"Verifying Checksum","progressDetail":{},"id":"d6cd23cd1a2c"}""")
            }

            on("processing another 'verifying checksum' event for the same layer") {
                val json = """{"status":"Verifying Checksum","progressDetail":{},"id":"d6cd23cd1a2c"}"""
                val progressUpdate by runNullableForEachTest { reporter.processRawProgressUpdate(json) }

                it("does not return an update") {
                    assertThat(progressUpdate, absent())
                }
            }

            on("processing a 'layer downloading' event for the same layer") {
                val json = """{"status":"Downloading","progressDetail":{"current":900,"total":4159},"id":"d6cd23cd1a2c"}"""
                val progressUpdate by runNullableForEachTest { reporter.processRawProgressUpdate(json) }

                it("returns an appropriate progress update") {
                    assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.Downloading, 900, 4159)))
                }
            }
        }

        given("all layers are downloading") {
            val progressUpdate by runNullableForEachTest {
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":300,"total":4000},"id":"d6cd23cd1a2c"}""")
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":600,"total":3000},"id":"55cbf04beb70"}""")
            }

            it("returns a progress update that indicates the image is downloading") {
                assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.Downloading, 900, 7000)))
            }
        }

        given("some layers are downloading and some have finished downloading") {
            val progressUpdate by runNullableForEachTest {
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":300,"total":4000},"id":"d6cd23cd1a2c"}""")
                reporter.processRawProgressUpdate("""{"status":"Download complete","progressDetail":{},"id":"d6cd23cd1a2c"}""")
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":600,"total":3000},"id":"55cbf04beb70"}""")
            }

            it("returns a progress update that indicates the image is downloading") {
                assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.Downloading, 4600, 7000)))
            }
        }

        given("some layers are downloading and some are extracting") {
            val progressUpdate by runNullableForEachTest {
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":300,"total":4000},"id":"d6cd23cd1a2c"}""")
                reporter.processRawProgressUpdate("""{"status":"Extracting","progressDetail":{"current":600,"total":3000},"id":"55cbf04beb70"}""")
            }

            it("returns a progress update that indicates the image is downloading") {
                assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.Downloading, 3300, 7000)))
            }
        }

        given("some layers are downloading, some are verifying checksums and some have finished downloading") {
            val progressUpdate by runNullableForEachTest {
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":50,"total":9000},"id":"4c60885e4f94"}""")
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":300,"total":4000},"id":"d6cd23cd1a2c"}""")
                reporter.processRawProgressUpdate("""{"status":"Verifying Checksum","progressDetail":{},"id":"d6cd23cd1a2c"}""")
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":600,"total":3000},"id":"55cbf04beb70"}""")
                reporter.processRawProgressUpdate("""{"status":"Download complete","progressDetail":{},"id":"55cbf04beb70"}""")
            }

            it("returns a progress update that indicates the image is downloading") {
                assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.Downloading, 7050, 16000)))
            }
        }

        given("some layers are verifying checksums and some have finished downloading") {
            val progressUpdate by runNullableForEachTest {
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":300,"total":4000},"id":"d6cd23cd1a2c"}""")
                reporter.processRawProgressUpdate("""{"status":"Verifying Checksum","progressDetail":{},"id":"d6cd23cd1a2c"}""")
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":600,"total":3000},"id":"55cbf04beb70"}""")
                reporter.processRawProgressUpdate("""{"status":"Download complete","progressDetail":{},"id":"55cbf04beb70"}""")
            }

            it("returns a progress update that indicates the checksum is being verified") {
                assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.VerifyingChecksum, 3000, 7000)))
            }
        }

        given("some layers have finished downloading and some are extracting") {
            val progressUpdate by runNullableForEachTest {
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":300,"total":4000},"id":"d6cd23cd1a2c"}""")
                reporter.processRawProgressUpdate("""{"status":"Extracting","progressDetail":{"current":700,"total":4000},"id":"d6cd23cd1a2c"}""")
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":600,"total":3000},"id":"55cbf04beb70"}""")
                reporter.processRawProgressUpdate("""{"status":"Download complete","progressDetail":{},"id":"55cbf04beb70"}""")
            }

            it("returns a progress update that indicates the image is extracting") {
                assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.Extracting, 700, 7000)))
            }
        }

        given("all layers have finished downloading") {
            val progressUpdate by runNullableForEachTest {
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":300,"total":4000},"id":"d6cd23cd1a2c"}""")
                reporter.processRawProgressUpdate("""{"status":"Download complete","progressDetail":{},"id":"d6cd23cd1a2c"}""")
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":600,"total":3000},"id":"55cbf04beb70"}""")
                reporter.processRawProgressUpdate("""{"status":"Download complete","progressDetail":{},"id":"55cbf04beb70"}""")
            }

            it("returns a progress update that indicates the image has finished downloading") {
                assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.DownloadComplete, 7000, 7000)))
            }
        }

        given("some layers have finished downloading and some have finished pulling") {
            val progressUpdate by runNullableForEachTest {
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":300,"total":4000},"id":"d6cd23cd1a2c"}""")
                reporter.processRawProgressUpdate("""{"status":"Download complete","progressDetail":{},"id":"d6cd23cd1a2c"}""")
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":600,"total":3000},"id":"55cbf04beb70"}""")
                reporter.processRawProgressUpdate("""{"status":"Pull complete","progressDetail":{},"id":"55cbf04beb70"}""")
            }

            it("returns a progress update that indicates the image has partly finished pulling") {
                assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.PullComplete, 3000, 7000)))
            }
        }

        given("some layers are extracting and some have finished pulling") {
            val progressUpdate by runNullableForEachTest {
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":300,"total":4000},"id":"d6cd23cd1a2c"}""")
                reporter.processRawProgressUpdate("""{"status":"Extracting","progressDetail":{"current":700,"total":4000},"id":"d6cd23cd1a2c"}""")
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":600,"total":3000},"id":"55cbf04beb70"}""")
                reporter.processRawProgressUpdate("""{"status":"Pull complete","progressDetail":{},"id":"55cbf04beb70"}""")
            }

            it("returns a progress update that indicates the image is extracting") {
                assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.Extracting, 3700, 7000)))
            }
        }

        given("all layers have finished pulling") {
            val progressUpdate by runNullableForEachTest {
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":300,"total":4000},"id":"d6cd23cd1a2c"}""")
                reporter.processRawProgressUpdate("""{"status":"Pull complete","progressDetail":{},"id":"d6cd23cd1a2c"}""")
                reporter.processRawProgressUpdate("""{"status":"Downloading","progressDetail":{"current":600,"total":3000},"id":"55cbf04beb70"}""")
                reporter.processRawProgressUpdate("""{"status":"Pull complete","progressDetail":{},"id":"55cbf04beb70"}""")
            }

            it("returns a progress update that indicates the image has finished pulling") {
                assertThat(progressUpdate, equalTo(ImagePullProgress(DownloadOperation.PullComplete, 7000, 7000)))
            }
        }
    }
})

private fun ImagePullProgressReporter.processRawProgressUpdate(json: String): ImagePullProgress? {
    val parsedJson = Json.default.parseToJsonElement(json).jsonObject

    return this.processProgressUpdate(parsedJson)
}
