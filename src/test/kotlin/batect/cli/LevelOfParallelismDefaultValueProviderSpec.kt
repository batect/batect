/*
   Copyright 2017 Charles Korn.

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

package batect.cli

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

object LevelOfParallelismDefaultValueProviderSpec : Spek({
    describe("a 'level of parallelism' default value provider") {
        val numberOfCPUs = Runtime.getRuntime().availableProcessors()
        val expectedLevelOfParallelism = numberOfCPUs * 2
        val provider = LevelOfParallelismDefaultValueProvider

        it("returns two times the number of CPUs as the default value") {
            assertThat(provider.value, equalTo(expectedLevelOfParallelism))
        }

        it("has a human-readable description of the value") {
            assertThat(provider.description, equalTo("defaults to two times the number of CPU cores available, which is $expectedLevelOfParallelism"))
        }
    }
})
