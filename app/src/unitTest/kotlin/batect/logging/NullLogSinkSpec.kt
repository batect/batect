/*
   Copyright 2017-2020 Charles Korn.

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
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object NullLogSinkSpec : Spek({
    describe("a null log sink") {
        val sink = NullLogSink()

        on("receiving a log message") {
            var invoked = false

            sink.write(Severity.Info, emptyMap()) {
                invoked = true
                this
            }

            it("does not invoke the builder function") {
                assertThat(invoked, equalTo(false))
            }
        }
    }
})
