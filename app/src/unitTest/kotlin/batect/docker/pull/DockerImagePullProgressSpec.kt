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

package batect.docker.pull

import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.startsWith
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DockerImagePullProgressSpec : Spek({
    describe("Docker image pull progress information") {
        given("the image is downloading") {
            val progress = DockerImagePullProgress("Downloading", 10, 100)

            on("formatting it for display to a user") {
                val display = progress.toStringForDisplay()

                it("formats the progress in the expected style") {
                    assertThat(display, equalTo("Downloading 10 B of 100 B (10%)"))
                }
            }
        }

        given("the total size is unknown") {
            val progress = DockerImagePullProgress("Downloading", 0, 0)

            on("formatting it for display to a user") {
                val display = progress.toStringForDisplay()

                it("does not display the size and just shows the percentage") {
                    assertThat(display, equalTo("Downloading (0%)"))
                }
            }
        }

        given("the pull has not started") {
            val progress = DockerImagePullProgress("Downloading", 0, 100)

            on("formatting it for display to a user") {
                val display = progress.toStringForDisplay()

                it("formats the progress in the expected style") {
                    assertThat(display, equalTo("Downloading 0 B of 100 B (0%)"))
                }
            }
        }

        given("the pull has completed") {
            val progress = DockerImagePullProgress("Downloading", 100, 100)

            on("formatting it for display to a user") {
                val display = progress.toStringForDisplay()

                it("formats the progress in the expected style") {
                    assertThat(display, equalTo("Downloading 100 B of 100 B (100%)"))
                }
            }
        }

        mapOf(
            10L to "10 B",
            100L to "100 B",
            999L to "999 B",
            1000L to "1.0 KB",
            1100L to "1.1 KB",
            (1000L * 1000) - 1 to "1000.0 KB",
            1000L * 1000 to "1.0 MB",
            10L * 1000 * 1000 to "10.0 MB",
            1000L * 1000 * 1000 to "1.0 GB",
            2L * 1000 * 1000 * 1000 to "2.0 GB",
            1000L * 1000 * 1000 * 1000 to "1.0 TB",
            2L * 1000 * 1000 * 1000 * 1000 to "2.0 TB"
        ).forEach { bytes, expectedBytesDisplay ->
            given("$bytes have been downloaded so far") {
                val progress = DockerImagePullProgress("Downloading", bytes, 100)

                on("formatting it for display to a user") {
                    val display = progress.toStringForDisplay()

                    it("formats the progress in the expected style") {
                        assertThat(display, startsWith("Downloading $expectedBytesDisplay of 100 B ("))
                    }
                }
            }

            given("$bytes need to be downloaded in total") {
                val progress = DockerImagePullProgress("Downloading", 100, bytes)

                on("formatting it for display to a user") {
                    val display = progress.toStringForDisplay()

                    it("formats the progress in the expected style") {
                        assertThat(display, startsWith("Downloading 100 B of $expectedBytesDisplay ("))
                    }
                }
            }
        }
    }
})
