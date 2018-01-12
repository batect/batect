/*
   Copyright 2017 Charles Korn.

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

package batect.model.events

import batect.model.steps.TaskStep
import batect.config.Container
import batect.model.BehaviourAfterFailure
import batect.os.Command
import kotlin.reflect.KClass

// TODO: do we need to provide higher-level methods (eg. getDockerContainerForContainer())?
// Seems like there could be a performance win to be had there because we can optimise for these
// common queries.
interface TaskEventContext {
    fun queueStep(step: TaskStep)
    fun abort()

    fun <T : TaskEvent> getPastEventsOfType(clazz: KClass<T>): Set<T>
    fun <T : TaskEvent> getSinglePastEventOfType(clazz: KClass<T>): T?

    fun <T : TaskStep> removePendingStepsOfType(clazz: KClass<T>)
    fun <T : TaskStep> getPendingAndProcessedStepsOfType(clazz: KClass<T>): Set<T>
    fun <T : TaskStep> getProcessedStepsOfType(clazz: KClass<T>): Set<T>

    fun isTaskContainer(container: Container): Boolean
    fun commandForContainer(container: Container): Command?
    fun additionalEnvironmentVariablesForContainer(container: Container): Map<String, String>
    fun dependenciesOf(container: Container): Set<Container>
    fun containersThatDependOn(container: Container): Set<Container>

    val allTaskContainers: Set<Container>
    val isAborting: Boolean
    val behaviourAfterFailure: BehaviourAfterFailure
    val projectName: String
}

inline fun <reified T: TaskEvent> TaskEventContext.getPastEventsOfType(): Set<T> = this.getPastEventsOfType(T::class)
inline fun <reified T: TaskEvent> TaskEventContext.getSinglePastEventOfType(): T? = this.getSinglePastEventOfType(T::class)
inline fun <reified T: TaskStep> TaskEventContext.removePendingStepsOfType() = this.removePendingStepsOfType(T::class)
inline fun <reified T: TaskStep> TaskEventContext.getPendingAndProcessedStepsOfType(): Set<T> = this.getPendingAndProcessedStepsOfType(T::class)
inline fun <reified T: TaskStep> TaskEventContext.getProcessedStepsOfType(): Set<T> = this.getProcessedStepsOfType(T::class)
