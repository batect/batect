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

package batect.execution.model.rules

import batect.execution.model.events.TaskEvent
import batect.execution.model.rules.cleanup.DeleteTaskNetworkStepRule
import batect.execution.model.rules.cleanup.DeleteTemporaryDirectoryStepRule
import batect.execution.model.rules.cleanup.DeleteTemporaryFileStepRule
import batect.execution.model.rules.cleanup.RemoveContainerStepRule
import batect.execution.model.rules.cleanup.StopContainerStepRule
import batect.execution.model.rules.run.BuildImageStepRule
import batect.execution.model.rules.run.CreateContainerStepRule
import batect.execution.model.rules.run.PrepareTaskNetworkStepRule
import batect.execution.model.rules.run.InitialiseCachesStepRule
import batect.execution.model.rules.run.PullImageStepRule
import batect.execution.model.rules.run.RunContainerSetupCommandsStepRule
import batect.execution.model.rules.run.RunContainerStepRule
import batect.execution.model.rules.run.WaitForContainerToBecomeHealthyStepRule
import batect.logging.LogMessageBuilder
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.set
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
    polymorphic(TaskStepRule::class) {
        BuildImageStepRule::class with BuildImageStepRule.serializer()
        CreateContainerStepRule::class with CreateContainerStepRule.serializer()
        PrepareTaskNetworkStepRule::class with PrepareTaskNetworkStepRule.serializer()
        DeleteTaskNetworkStepRule::class with DeleteTaskNetworkStepRule.serializer()
        DeleteTemporaryDirectoryStepRule::class with DeleteTemporaryDirectoryStepRule.serializer()
        DeleteTemporaryFileStepRule::class with DeleteTemporaryFileStepRule.serializer()
        InitialiseCachesStepRule::class with InitialiseCachesStepRule.serializer()
        PullImageStepRule::class with PullImageStepRule.serializer()
        RemoveContainerStepRule::class with RemoveContainerStepRule.serializer()
        RunContainerSetupCommandsStepRule::class with RunContainerSetupCommandsStepRule.serializer()
        RunContainerStepRule::class with RunContainerStepRule.serializer()
        StopContainerStepRule::class with StopContainerStepRule.serializer()
        WaitForContainerToBecomeHealthyStepRule::class with WaitForContainerToBecomeHealthyStepRule.serializer()
    }
}

fun LogMessageBuilder.data(name: String, rules: Set<TaskStepRule>) = this.data(name, rules, TaskStepRule.serializer().set)
fun LogMessageBuilder.data(name: String, rule: TaskStepRule) = this.data(name, rule, TaskStepRule.serializer())
