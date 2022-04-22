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

import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DockerVolumeMountSpec : Spek({
    describe("a Docker volume mount") {
        given("a volume mount with a local path as the source") {
            val source = DockerVolumeMountSource.LocalPath("/local")

            given("it has no options") {
                val mount = DockerVolumeMount(source, "/container")

                it("converts to a string in Docker volume mount format with no options included") {
                    assertThat(mount.toString(), equalTo("/local:/container"))
                }
            }

            given("it has some options") {
                val mount = DockerVolumeMount(source, "/container", "some-options")

                it("converts to a string in Docker volume mount format with the options included") {
                    assertThat(mount.toString(), equalTo("/local:/container:some-options"))
                }
            }
        }

        given("a volume mount with a volume as the source") {
            val source = DockerVolumeMountSource.Volume("my-volume")

            given("it has no options") {
                val mount = DockerVolumeMount(source, "/container")

                it("converts to a string in Docker volume mount format with no options included") {
                    assertThat(mount.toString(), equalTo("my-volume:/container"))
                }
            }

            given("it has some options") {
                val mount = DockerVolumeMount(source, "/container", "some-options")

                it("converts to a string in Docker volume mount format with the options included") {
                    assertThat(mount.toString(), equalTo("my-volume:/container:some-options"))
                }
            }
        }
    }
})
