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

package batect.ioc

import batect.cli.CommandLineOptions
import batect.config.Configuration
import batect.config.Container
import batect.config.ContainerMap
import batect.config.PullImage
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.os.HostEnvironmentVariables
import batect.testutils.doesNotThrow
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object InjectionConfigurationSpec : Spek({
    describe("Kodein injection configuration") {
        val container = Container("the-container", PullImage("test-image"))
        val task = Task("the-task", TaskRunConfiguration(container.name))
        val config = Configuration("project", TaskMap(task), ContainerMap(container))

        val baseConfiguration = createKodeinConfiguration(PrintStream(ByteArrayOutputStream()), PrintStream(ByteArrayOutputStream()), ByteArrayInputStream(ByteArray(0)))
        val afterCommandLineOptions = CommandLineOptions(taskName = task.name).extend(baseConfiguration)
        val inDockerDaemonContext = DockerConfigurationKodeinFactory(afterCommandLineOptions).create(mock())
        val inSessionContext = SessionKodeinFactory(inDockerDaemonContext, HostEnvironmentVariables(), mock()).create(config)
        val inTaskContext = TaskKodeinFactory(inSessionContext).create(task, mock())

        describe("each registered class") {
            inTaskContext.container.tree.bindings.keys.forEach { key ->
                on("attempting to get an instance matching $key") {
                    it("does not throw an invalid configuration exception") {
                        assertThat({ inTaskContext.Instance(key.type, key.tag) }, doesNotThrow())
                    }
                }
            }
        }
    }
})
