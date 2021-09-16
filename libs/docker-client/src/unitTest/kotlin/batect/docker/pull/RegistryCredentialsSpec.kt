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

import batect.testutils.given
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RegistryCredentialsSpec : Spek({
    describe("a set of Docker registry credentials") {
        given("a user name, password and server address") {
            val credentials = PasswordRegistryCredentials(
                "some-user",
                "super-secret-password",
                "my-server.com"
            )

            on("serialising the credentials to JSON") {
                val json = credentials.toJSON().toString()

                it("returns the credentials in the format expected by the Docker API") {
                    assertThat(
                        json,
                        equivalentTo(
                            """
                                |{
                                |   "username": "some-user",
                                |   "password": "super-secret-password",
                                |   "email": "",
                                |   "serveraddress": "my-server.com"
                                |}
                            """.trimMargin()
                        )
                    )
                }
            }
        }

        given("a token") {
            val credentials = TokenRegistryCredentials("some-token", "some-server.com")

            on("serialising the credentials to JSON") {
                val json = credentials.toJSON().toString()

                it("returns the credentials in the format expected by the Docker API") {
                    assertThat(
                        json,
                        equivalentTo(
                            """
                                |{
                                |   "identitytoken": "some-token"
                                |}
                            """.trimMargin()
                        )
                    )
                }
            }
        }
    }
})
