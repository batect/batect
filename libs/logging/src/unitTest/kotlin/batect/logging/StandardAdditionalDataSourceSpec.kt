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

package batect.logging

import batect.testutils.on
import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.describe
import jnr.posix.POSIX
import kotlinx.serialization.builtins.serializer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object StandardAdditionalDataSourceSpec : Spek({
    describe("a standard additional data source") {
        val posix = mock<POSIX> {
            on { getpid() } doReturn 456
        }

        val thread = mock<Thread> {
            on { id } doReturn 123
            on { name } doReturn "The awesome thread"
        }

        val source = StandardAdditionalDataSource(posix, { thread })

        on("generating the set of additional data") {
            val data = source.getAdditionalData()

            it("includes the current process' ID") {
                assertThat(data, hasKeyWithValue("@processId", JsonableObject(456, Int.serializer())))
            }

            it("includes the current thread's ID") {
                assertThat(data, hasKeyWithValue("@threadId", JsonableObject(123L, Long.serializer())))
            }

            it("includes the current thread's name") {
                assertThat(data, hasKeyWithValue("@threadName", JsonableObject("The awesome thread", String.serializer())))
            }
        }
    }
})

fun hasKeyWithValue(key: String, value: Any?): Matcher<Map<String, Any?>> = object : Matcher.Primitive<Map<String, Any?>>() {
    override fun invoke(actual: Map<String, Any?>): MatchResult {
        if (actual.containsKey(key) && actual.get(key) == value) {
            return MatchResult.Match
        } else {
            return MatchResult.Mismatch("was ${describe(actual)}")
        }
    }

    override val description: String get() = "contains entry with key ${describe(key)} and value ${describe(value)}"
    override val negatedDescription: String get() = "does not contain entry with key ${describe(key)} and value ${describe(value)}"
}
