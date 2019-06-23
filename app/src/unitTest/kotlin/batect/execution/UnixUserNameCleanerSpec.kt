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

package batect.execution

import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

// Source: https://unix.stackexchange.com/questions/157426/what-is-the-regex-to-validate-linux-users
// Rules are:
// Must be lowercase
// Can contain a-z, 0-9, underscore and hyphen, but must start with letter
// Last character can be dollar sign
// Must not be longer than 32 characters
object UnixUserNameCleanerSpec : Spek({
    describe("a Unix user name cleaner") {
        val userNameCleaner = UnixUserNameCleaner()

        listOf(
            "a",
            "user",
            "a1",
            "a$",
            "a_",
            "a-",
            "a_a",
            "a-a",
            "a2345678901234567890123456789012"
        ).forEach { userName ->
            given("the user name '$userName'") {
                it("returns the user name unchanged") {
                    assertThat(userNameCleaner.clean(userName), equalTo(userName))
                }
            }
        }

        listOf(
            "",
            " ",
            "1",
            "$",
            "_",
            "-",
            "#"
        ).forEach { userName ->
            given("the user name '$userName'") {
                it("returns the default user name") {
                    assertThat(userNameCleaner.clean(userName), equalTo("default-user-name"))
                }
            }
        }

        mapOf(
            "A" to "a",
            "the user" to "theuser",
            "the#user" to "theuser",
            "TheUser" to "theuser",
            "c$$" to "c$",
            "1aa" to "aa",
            "_aa" to "aa",
            "-aa" to "aa",
            "a23456789012345678901234567890123" to "a2345678901234567890123456789012"
        ).forEach { originalUserName, cleanedUserName ->
            given("the user name '$originalUserName'") {
                it("returns the cleaned form of the user name") {
                    assertThat(userNameCleaner.clean(originalUserName), equalTo(cleanedUserName))
                }
            }
        }
    }
})
