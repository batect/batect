/*
   Copyright 2017-2018 Charles Korn.

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

import batect.testutils.equalTo
import com.natpryce.hamkrest.assertion.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it

object DockerRegistryIndexResolverSpec : Spek({
    describe("a Docker registry index resolver") {
        val resolver = DockerRegistryIndexResolver()

        given("the default registry domain name") {
            it("returns the default index URL") {
                assertThat(resolver.resolveRegistryIndex("docker.io"), equalTo("https://index.docker.io/v1/"))
            }
        }

        given("a non-default registry domain name") {
            it("returns that domain as the index name") {
                assertThat(resolver.resolveRegistryIndex("somethingelse.com"), equalTo("somethingelse.com"))
            }
        }
    }
})
