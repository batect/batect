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

import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.runners.BuildImageStepRunner
import batect.execution.model.steps.runners.CreateContainerStepRunner
import batect.execution.model.steps.runners.DeleteTaskNetworkStepRunner
import batect.execution.model.steps.runners.PrepareTaskNetworkStepRunner
import batect.execution.model.steps.runners.PullImageStepRunner
import batect.execution.model.steps.runners.RemoveContainerStepRunner
import batect.execution.model.steps.runners.RunContainerSetupCommandsStepRunner
import batect.execution.model.steps.runners.RunContainerStepRunner
import batect.execution.model.steps.runners.StopContainerStepRunner
import batect.execution.model.steps.runners.WaitForContainerToBecomeHealthyStepRunner
import org.kodein.di.DirectDI
import org.kodein.di.instance

class TaskStepRunner(private val kodein: DirectDI) {
    fun run(step: TaskStep, eventSink: TaskEventSink) {
        when (step) {
            is BuildImageStep -> kodein.instance<BuildImageStepRunner>().run(step, eventSink)
            is PullImageStep -> kodein.instance<PullImageStepRunner>().run(step, eventSink)
            is PrepareTaskNetworkStep -> kodein.instance<PrepareTaskNetworkStepRunner>().run(eventSink)
            is CreateContainerStep -> kodein.instance<CreateContainerStepRunner>().run(step, eventSink)
            is RunContainerStep -> kodein.instance<RunContainerStepRunner>().run(step, eventSink)
            is WaitForContainerToBecomeHealthyStep -> kodein.instance<WaitForContainerToBecomeHealthyStepRunner>().run(step, eventSink)
            is RunContainerSetupCommandsStep -> kodein.instance<RunContainerSetupCommandsStepRunner>().run(step, eventSink)
            is StopContainerStep -> kodein.instance<StopContainerStepRunner>().run(step, eventSink)
            is RemoveContainerStep -> kodein.instance<RemoveContainerStepRunner>().run(step, eventSink)
            is DeleteTaskNetworkStep -> kodein.instance<DeleteTaskNetworkStepRunner>().run(step, eventSink)
        }
    }
}
