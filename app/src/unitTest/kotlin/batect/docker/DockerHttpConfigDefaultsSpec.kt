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

package batect.docker

import batect.os.OperatingSystem
import batect.os.SystemInfo
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DockerHttpConfigDefaultsSpec : Spek({
    describe("a set of default values for Docker communication") {
        mapOf(
            OperatingSystem.Linux to "unix:///var/run/docker.sock",
            OperatingSystem.Mac to "unix:///var/run/docker.sock",
            OperatingSystem.Windows to "npipe:////./pipe/docker_engine"
        ).forEach { (os, expected) ->
            given("the application is running on $os") {
                val systemInfo by createForEachTest {
                    mock<SystemInfo> {
                        on { operatingSystem } doReturn os
                    }
                }

                val defaults by createForEachTest { DockerHttpConfigDefaults(systemInfo) }

                it("returns the expected default Docker daemon address") {
                    assertThat(defaults.defaultDockerHost, equalTo(expected))
                }
            }
        }

        given("the application is running on an unknown operating system") {
            val systemInfo by createForEachTest {
                mock<SystemInfo> {
                    on { operatingSystem } doReturn OperatingSystem.Other
                }
            }

            val defaults by createForEachTest { DockerHttpConfigDefaults(systemInfo) }

            it("throws an appropriate exception") {
                assertThat({ defaults.defaultDockerHost }, throws<IllegalArgumentException>(withMessage("Unknown operating system.")))
            }
        }
    }
})
