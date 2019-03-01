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

package batect.execution

import batect.config.Configuration
import batect.config.Container
import batect.config.ContainerMap
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.logging.Logger
import batect.logging.Severity
import batect.testutils.InMemoryLogSink
import batect.testutils.hasMessage
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.testutils.withAdditionalData
import batect.testutils.withLogMessage
import batect.testutils.withSeverity
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ContainerDependencyGraphProviderSpec : Spek({
    describe("a container dependency graph provider") {
        val logSink = InMemoryLogSink()
        val logger = Logger("some.source", logSink)
        val commandResolver = mock<ContainerCommandResolver>()
        val provider = ContainerDependencyGraphProvider(commandResolver, logger)

        on("creating a dependency graph") {
            val dependencyForContainer = Container("dependencyForContainer", imageSourceDoesNotMatter())
            val dependencyForTask = Container("dependencyForTask", imageSourceDoesNotMatter())
            val mainContainer = Container("mainContainer", imageSourceDoesNotMatter(), dependencies = setOf(dependencyForContainer.name))
            val task = Task("mainTask", TaskRunConfiguration(mainContainer.name), dependsOnContainers = setOf(dependencyForTask.name))
            val config = Configuration("some_project", TaskMap(task), ContainerMap(mainContainer, dependencyForContainer, dependencyForTask))

            provider.createGraph(config, task)

            it("logs the details of the created dependency graph") {
                assertThat(logSink, hasMessage(
                    withSeverity(Severity.Info) and
                        withLogMessage("Dependency graph for task created.") and
                        withAdditionalData("task", task) and
                        withAdditionalData("dependencies", mapOf(
                            mainContainer.name to setOf(dependencyForContainer.name, dependencyForTask.name),
                            dependencyForContainer.name to emptySet(),
                            dependencyForTask.name to emptySet()
                        ))
                ))
            }
        }
    }
})
