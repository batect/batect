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

package batect.execution.model.rules.run

import batect.config.Container
import batect.docker.DockerContainer
import batect.execution.model.steps.RunContainerStep
import batect.execution.model.steps.TaskStep

data class RunContainerStepRule(override val container: Container, override val dependencies: Set<Container>) : StartContainerStepRuleBase(container, dependencies) {
    override fun createStep(dockerContainer: DockerContainer): TaskStep = RunContainerStep(container, dockerContainer)

    override fun toString(): String = super.toString()
}
