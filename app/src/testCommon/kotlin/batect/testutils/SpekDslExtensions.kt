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

package batect.testutils

import org.spekframework.spek2.dsl.Skip
import org.spekframework.spek2.style.specification.Suite

fun Suite.given(description: String, body: Suite.() -> Unit) = context("given $description", Skip.No, body)
fun Suite.on(description: String, body: Suite.() -> Unit) = describe("on $description", Skip.No, body)
