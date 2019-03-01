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

package batect.docker.run

import batect.docker.DockerAPI
import batect.docker.DockerContainer
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.on
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.TimeUnit

object ContainerWaiterSpec : Spek({
    describe("a container waiter") {
        val api by createForEachTest { mock<DockerAPI>() }
        val waiter by createForEachTest { ContainerWaiter(api) }

        on("starting to wait for a container to exit") {
            val container = DockerContainer("the-container")

            beforeEachTest { whenever(api.waitForExit(container)).doReturn(123) }

            val future by runForEachTest { waiter.startWaitingForContainerToExit(container) }
            val exitCode by runForEachTest { future.get(1, TimeUnit.SECONDS) }

            it("returns a future that returns the exit code of the container on completion") {
                assertThat(exitCode, equalTo(123))
            }
        }
    }
})
