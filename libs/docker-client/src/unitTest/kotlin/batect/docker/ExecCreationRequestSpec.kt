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

package batect.docker

import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ExecCreationRequestSpec : Spek({
    describe("an exec instance creation request") {
        describe("converting a request to JSON") {
            describe("when a user and group is provided") {
                val request = ExecCreationRequest(
                    attachStdin = false,
                    attachStdout = true,
                    attachStderr = false,
                    attachTty = true,
                    environmentVariables = mapOf("BLAH" to "blah-value"),
                    command = listOf("some-thing", "some-arg"),
                    privileged = false,
                    userAndGroup = UserAndGroup(123, 456),
                    workingDirectory = "/some/work/dir"
                )

                val json by runForEachTest { request.toJson() }

                it("returns the request in the expected format") {
                    assertThat(
                        json,
                        equivalentTo(
                            """
                                {
                                    "AttachStdin": false,
                                    "AttachStdout": true,
                                    "AttachStderr": false,
                                    "Tty": true,
                                    "Env": ["BLAH=blah-value"],
                                    "Cmd": ["some-thing", "some-arg"],
                                    "Privileged": false,
                                    "User": "123:456",
                                    "WorkingDir": "/some/work/dir"
                                }
                            """.trimIndent()
                        )
                    )
                }
            }

            describe("when a user and group is not provided") {
                val request = ExecCreationRequest(
                    attachStdin = false,
                    attachStdout = true,
                    attachStderr = false,
                    attachTty = true,
                    environmentVariables = mapOf("BLAH" to "blah-value"),
                    command = listOf("some-thing", "some-arg"),
                    privileged = false,
                    userAndGroup = null,
                    workingDirectory = "/some/work/dir"
                )

                val json by runForEachTest { request.toJson() }

                it("does not include an entry for the user and group") {
                    assertThat(
                        json,
                        equivalentTo(
                            """
                                {
                                    "AttachStdin": false,
                                    "AttachStdout": true,
                                    "AttachStderr": false,
                                    "Tty": true,
                                    "Env": ["BLAH=blah-value"],
                                    "Cmd": ["some-thing", "some-arg"],
                                    "Privileged": false,
                                    "WorkingDir": "/some/work/dir"
                                }
                            """.trimIndent()
                        )
                    )
                }
            }
        }
    }
})
