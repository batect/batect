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

package batect.config

import batect.testutils.on
import batect.testutils.osIndependentPath
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object BuildImageSpec : Spek({
    describe("a image build source") {
        val source = BuildImage(
            osIndependentPath("/image-build-dir"),
            mapOf(
                "some_arg" to LiteralValue("some_value"),
                "some_other_arg" to EnvironmentVariableReference("host_var"),
                "some_config_var" to ConfigVariableReference("config_var")
            ),
            "some-Dockerfile-path"
        )

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assertThat(source.toString(), equalTo(
                    "BuildImage(" +
                        "build directory: '/image-build-dir', " +
                        "build args: [some_arg=LiteralValue(value: 'some_value'), some_other_arg=EnvironmentVariableReference(reference to: 'host_var', default: null), some_config_var=ConfigVariableReference(reference to: 'config_var')], " +
                        "Dockerfile path: 'some-Dockerfile-path')"
                ))
            }
        }
    }
})
