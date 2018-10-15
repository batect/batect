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

package batect.docker.run

import batect.docker.DockerAPI
import batect.docker.DockerContainer
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.util.concurrent.TimeUnit

object ContainerWaiterSpec : Spek({
    describe("a container waiter") {
        val api by createForEachTest { mock<DockerAPI>() }
        val waiter by createForEachTest { ContainerWaiter(api) }

        on("starting to wait for a container to exit") {
            val container = DockerContainer("the-container")

            whenever(api.waitForExit(container)).doReturn(123)

            val future = waiter.startWaitingForContainerToExit(container)
            val exitCode = future.get(1, TimeUnit.SECONDS)

            it("returns a future that returns the exit code of the container on completion") {
                assertThat(exitCode, equalTo(123))
            }
        }
    }
})
