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

package batect.execution

import batect.config.VolumeMount
import batect.docker.DockerVolumeMount
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object VolumeMountResolverSpec : Spek({
    describe("a volume mount resolver") {
        val resolver by createForEachTest { VolumeMountResolver() }

        given("a set of volume mounts from the configuration file") {
            val mounts = setOf(
                VolumeMount("/local-1", "/container-1"),
                VolumeMount("/local-2", "/container-2", "options-2")
            )

            it("resolves the local mount paths, preserving the container path and options") {
                assertThat(resolver.resolve(mounts), equalTo(setOf(
                    DockerVolumeMount("/local-1", "/container-1"),
                    DockerVolumeMount("/local-2", "/container-2", "options-2")
                )))
            }
        }
    }
})
