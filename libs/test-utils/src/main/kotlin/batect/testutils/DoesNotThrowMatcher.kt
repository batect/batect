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

package batect.testutils

import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import java.io.ByteArrayOutputStream
import java.io.PrintStream

fun doesNotThrow(): Matcher<() -> Unit> {
    return object : Matcher<() -> Unit> {
        override fun invoke(actual: () -> Unit): MatchResult =
            try {
                actual()
                MatchResult.Match
            } catch (e: Throwable) {
                val stream = ByteArrayOutputStream()
                val printer = PrintStream(stream)
                e.printStackTrace(printer)
                printer.flush()
                MatchResult.Mismatch("threw $stream")
            }

        override val description: String get() = "does not throw an exception"
        override val negatedDescription: String get() = "does throw an exception"
    }
}
