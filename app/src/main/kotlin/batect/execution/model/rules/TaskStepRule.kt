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

package batect.execution.model.rules

import batect.execution.model.events.TaskEvent
import batect.execution.model.rules.cleanup.DeleteTaskNetworkStepRule
import batect.execution.model.rules.cleanup.RemoveContainerStepRule
import batect.execution.model.rules.cleanup.StopContainerStepRule
import batect.execution.model.rules.run.BuildImageStepRule
import batect.execution.model.rules.run.CreateContainerStepRule
import batect.execution.model.rules.run.PrepareTaskNetworkStepRule
import batect.execution.model.rules.run.PullImageStepRule
import batect.execution.model.rules.run.RunContainerSetupCommandsStepRule
import batect.execution.model.rules.run.RunContainerStepRule
import batect.execution.model.rules.run.WaitForContainerToBecomeHealthyStepRule
import batect.logging.LogMessageBuilder
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.modules.SerializersModule

@Serializable
@Polymorphic
abstract class TaskStepRule {
    abstract fun evaluate(pastEvents: Set<TaskEvent>): TaskStepRuleEvaluationResult

    protected inline fun <reified T : TaskEvent> Set<TaskEvent>.singleInstance(predicate: (T) -> Boolean): T =
        this.filterIsInstance<T>().single(predicate)

    protected inline fun <reified T : TaskEvent> Set<TaskEvent>.singleInstanceOrNull(predicate: (T) -> Boolean): T? =
        this.filterIsInstance<T>().singleOrNull(predicate)

    protected inline fun <reified T : TaskEvent> Set<TaskEvent>.singleInstanceOrNull(): T? =
        this.filterIsInstance<T>().singleOrNull()
}

val serializersModule = SerializersModule {
    polymorphic(TaskStepRule::class, BuildImageStepRule::class, BuildImageStepRule.serializer())
    polymorphic(TaskStepRule::class, CreateContainerStepRule::class, CreateContainerStepRule.serializer())
    polymorphic(TaskStepRule::class, PrepareTaskNetworkStepRule::class, PrepareTaskNetworkStepRule.serializer())
    polymorphic(TaskStepRule::class, DeleteTaskNetworkStepRule::class, DeleteTaskNetworkStepRule.serializer())
    polymorphic(TaskStepRule::class, PullImageStepRule::class, PullImageStepRule.serializer())
    polymorphic(TaskStepRule::class, RemoveContainerStepRule::class, RemoveContainerStepRule.serializer())
    polymorphic(TaskStepRule::class, RunContainerSetupCommandsStepRule::class, RunContainerSetupCommandsStepRule.serializer())
    polymorphic(TaskStepRule::class, RunContainerStepRule::class, RunContainerStepRule.serializer())
    polymorphic(TaskStepRule::class, StopContainerStepRule::class, StopContainerStepRule.serializer())
    polymorphic(TaskStepRule::class, WaitForContainerToBecomeHealthyStepRule::class, WaitForContainerToBecomeHealthyStepRule.serializer())
}

fun LogMessageBuilder.data(name: String, rules: Set<TaskStepRule>) = this.data(name, rules, SetSerializer(TaskStepRule.serializer()))
fun LogMessageBuilder.data(name: String, rule: TaskStepRule) = this.data(name, rule, TaskStepRule.serializer())
