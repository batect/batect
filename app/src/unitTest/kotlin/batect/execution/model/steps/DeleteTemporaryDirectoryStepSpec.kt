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

package batect.execution.model.steps

import batect.testutils.logRepresentationOf
import batect.testutils.on
import batect.testutils.osIndependentPath
import com.natpryce.hamkrest.assertion.assertThat
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DeleteTemporaryDirectoryStepSpec : Spek({
    describe("a 'delete temporary directory' step") {
        val path = osIndependentPath("/some-file")
        val step = DeleteTemporaryDirectoryStep(path)

        on("attaching it to a log message") {
            it("returns a machine-readable representation of itself") {
                assertThat(
                    logRepresentationOf(step),
                    equivalentTo(
                        """
                        |{
                        |   "type": "${step::class.qualifiedName}",
                        |   "directoryPath": "/some-file"
                        |}
                        """.trimMargin()
                    )
                )
            }
        }
    }
})
