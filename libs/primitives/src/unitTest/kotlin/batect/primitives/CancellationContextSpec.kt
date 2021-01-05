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

package batect.primitives

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CancellationContextSpec : Spek({
    describe("a cancellation context") {
        val context by createForEachTest { CancellationContext() }

        given("a cancellation callback has been registered") {
            var callbackCount = 0

            beforeEachTest {
                callbackCount = 0
                context.addCancellationCallback { callbackCount++ }
            }

            on("before the context is cancelled") {
                it("does not call the registered callback") {
                    assertThat(callbackCount, equalTo(0))
                }
            }

            on("cancelling the context") {
                beforeEachTest { context.cancel() }

                it("calls the registered callback") {
                    assertThat(callbackCount, equalTo(1))
                }
            }

            on("cancelling the context multiple times") {
                beforeEachTest {
                    context.cancel()
                    context.cancel()
                }

                it("calls the registered callback only once") {
                    assertThat(callbackCount, equalTo(1))
                }
            }
        }

        given("a cancellation callback has been registered and removed") {
            var callbackCount = 0

            beforeEachTest {
                callbackCount = 0
                val closeable = context.addCancellationCallback { callbackCount++ }
                closeable.use {}
            }

            on("cancelling the context") {
                beforeEachTest { context.cancel() }

                it("does not call the callback") {
                    assertThat(callbackCount, equalTo(0))
                }
            }
        }

        given("multiple cancellation callbacks have been registered") {
            var callback1Count = 0
            var callback2Count = 0

            beforeEachTest {
                callback1Count = 0
                callback2Count = 0

                context.addCancellationCallback { callback1Count++ }
                context.addCancellationCallback { callback2Count++ }
            }

            on("cancelling the context") {
                beforeEachTest { context.cancel() }

                it("calls both registered callbacks") {
                    assertThat(callback1Count, equalTo(1))
                    assertThat(callback2Count, equalTo(1))
                }
            }
        }

        given("the cancellation context has been cancelled") {
            beforeEachTest { context.cancel() }

            on("registering a callback") {
                var callbackCount = 0

                beforeEachTest {
                    callbackCount = 0
                    context.addCancellationCallback { callbackCount++ }.use {}
                }

                it("immediately calls the callback") {
                    assertThat(callbackCount, equalTo(1))
                }
            }
        }
    }
})
