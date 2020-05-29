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

package batect.journeytests

import batect.journeytests.testutils.ApplicationRunner
import batect.testutils.createForGroup
import batect.testutils.on
import batect.testutils.runBeforeGroup
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

abstract class SimpleTaskJourneyTest(val testName: String, val description: String): Spek({
    describe(description) {
        val runner by createForGroup { ApplicationRunner(testName) }

        on("running that task") {
            val result by runBeforeGroup { runner.runApplication(listOf("the-task")) }

            it("prints the output from that task") {
                assertThat(result.output, containsSubstring("This is some output from the task\n"))
            }

            it("returns the exit code from that task") {
                assertThat(result.exitCode, equalTo(123))
            }
        }
    }
})

object SimpleTaskUsingDockerfileJourneyTest : SimpleTaskJourneyTest("simple-task-using-dockerfile", "a simple task that uses a Dockerfile with the command specified on the task in the configuration file")
object SimpleTaskUsingImageJourneyTest : SimpleTaskJourneyTest("simple-task-using-image", "a simple task that uses an existing image")
object SimpleTaskUsingCustomDockerfileJourneyTest : SimpleTaskJourneyTest("simple-task-using-custom-dockerfile", "a simple task that uses a Dockerfile in a non-standard location")
object SimpleTaskUsingDockerfileCommandJourneyTest : SimpleTaskJourneyTest("simple-task-dockerfile-command", "a simple task with the command specified in the Dockerfile")
object SimpleTaskUsingContainerCommandJourneyTest : SimpleTaskJourneyTest("simple-task-container-command", "a simple task with the command specified on the container in the configuration file")
object SimpleTaskWithEntrypointOnContainerJourneyTest : SimpleTaskJourneyTest("simple-task-entrypoint-on-container", "a simple task with an entrypoint specified on the container in the configuration file")
object SimpleTaskWithEntrypointOnTaskJourneyTest : SimpleTaskJourneyTest("simple-task-entrypoint-on-task", "a simple task with an entrypoint specified on the task in the configuration file")
object SimpleTaskWithEnvironmentJourneyTest : SimpleTaskJourneyTest("simple-task-with-environment", "a simple task with a task-level environment variable")
object ContainerWithHealthCheckOverridesJourneyTest : SimpleTaskJourneyTest("container-with-health-check-overrides", "a task with a dependency container that has a batect-specific health check configuration")
object BuildImageWithDockerignoreJourneyTest : SimpleTaskJourneyTest("build-image-dockerignore", "a task that builds an image with a .dockerignore file")
object SimpleTaskUsingDockerfileWithAddFromUrlJourneyTest : SimpleTaskJourneyTest("simple-task-using-dockerfile-with-add-from-url", "a simple task that uses a Dockerfile with an ADD command that downloads a file from a URL")
object DependencyContainerWithSetupCommandJourneyTest : SimpleTaskJourneyTest("dependency-container-with-setup-command", "a simple task that uses a setup command on a dependency container")
object TaskContainerWithSetupCommandJourneyTest : SimpleTaskJourneyTest("task-container-with-setup-command", "a simple task that uses a setup command on the task container")
object ConfigWithIncludesJourneyTest : SimpleTaskJourneyTest("config-with-includes", "a configuration file that uses includes")
