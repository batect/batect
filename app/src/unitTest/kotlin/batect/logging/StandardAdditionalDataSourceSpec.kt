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

package batect.logging

import batect.testutils.hasKeyWithValue
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import jnr.posix.POSIX
import kotlinx.serialization.internal.IntSerializer
import kotlinx.serialization.internal.LongSerializer
import kotlinx.serialization.internal.StringSerializer
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
                assertThat(data, hasKeyWithValue("@processId", JsonableObject(456, IntSerializer)))
            }

            it("includes the current thread's ID") {
                assertThat(data, hasKeyWithValue("@threadId", JsonableObject(123L, LongSerializer)))
            }

            it("includes the current thread's name") {
                assertThat(data, hasKeyWithValue("@threadName", JsonableObject("The awesome thread", StringSerializer)))
            }
        }
    }
})
