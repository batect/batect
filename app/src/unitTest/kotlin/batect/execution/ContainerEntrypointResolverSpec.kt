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

import batect.config.Container
import batect.config.Task
import batect.config.TaskRunConfiguration
import batect.os.Command
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ContainerEntrypointResolverSpec : Spek({
    describe("a container entrypoint resolver") {
        val resolver by createForEachTest { ContainerEntrypointResolver() }
        val container by createForEachTest { Container("the-container", imageSourceDoesNotMatter(), entrypoint = Command.parse("the-container-entrypoint")) }

        given("the container is the task container") {
            val taskContainerName by createForEachTest { container.name }

            given("the task does not override the entrypoint for the task container") {
                val task by createForEachTest { Task("the-task", TaskRunConfiguration(taskContainerName, entrypoint = null)) }

                it("returns the container's entrypoint") {
                    assertThat(resolver.resolveEntrypoint(container, task), equalTo(Command.parse("the-container-entrypoint")))
                }
            }

            given("the task overrides the entrypoint for the task container") {
                val task by createForEachTest { Task("the-task", TaskRunConfiguration(taskContainerName, entrypoint = Command.parse("the-task-entrypoint"))) }

                it("returns the container's entrypoint") {
                    assertThat(resolver.resolveEntrypoint(container, task), equalTo(Command.parse("the-task-entrypoint")))
                }
            }
        }

        given("the container is not the task container") {
            val taskContainerName = "some-other-container"
            val task by createForEachTest { Task("the-task", TaskRunConfiguration(taskContainerName, entrypoint = Command.parse("the-task-entrypoint"))) }

            it("returns the container's entrypoint") {
                assertThat(resolver.resolveEntrypoint(container, task), equalTo(Command.parse("the-container-entrypoint")))
            }
        }
    }
})
