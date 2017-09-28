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

package batect.model

import batect.config.Container
import batect.logging.LoggerFactory
import batect.model.events.TaskEvent
import batect.model.events.TaskEventContext
import batect.model.events.TaskEventSink
import batect.model.steps.TaskStep
import batect.utils.mapToSet
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

// FIXME: this is really two things: the state machine and a collection of utility functions
// for events
class TaskStateMachine(private val graph: DependencyGraph, private val loggerFactory: LoggerFactory) : TaskEventContext, TaskEventSink {
    private val stepQueue: Queue<TaskStep> = LinkedList<TaskStep>()
    private val processedSteps = mutableSetOf<TaskStep>()
    private val processedEvents = mutableSetOf<TaskEvent>()

    // TODO: how to ensure that methods like queueStep() are only called from within
    // TaskEvent.apply() methods (which should be executed under the lock)?
    private val lock = ReentrantLock()

    override fun queueStep(step: TaskStep) {
        stepQueue.add(step)
    }

    fun popNextStep(): TaskStep? {
        lock.withLock {
            val step = stepQueue.poll()

            if (step != null) {
                processedSteps.add(step)
            }

            return step
        }
    }

    override fun <T : TaskStep> getPendingAndProcessedStepsOfType(clazz: KClass<T>): Set<T> {
        val pendingSteps = stepQueue.filterIsInstanceTo(mutableSetOf(), clazz.java)
        val processedSteps = getProcessedStepsOfType(clazz)
        return pendingSteps + processedSteps
    }

    override fun <T : TaskStep> getProcessedStepsOfType(clazz: KClass<T>): Set<T> {
        return processedSteps.filterIsInstanceTo(mutableSetOf(), clazz.java)
    }

    override fun <T : TaskStep> removePendingStepsOfType(clazz: KClass<T>) {
        stepQueue.removeIf { clazz.isInstance(it) }
    }

    override fun abort() {
        isAborting = true
    }

    override fun postEvent(event: TaskEvent) {
        lock.withLock {
            processedEvents.add(event)
            event.apply(this, loggerFactory.createLoggerForClass(event::class))
        }
    }

    override fun <T : TaskEvent> getPastEventsOfType(clazz: KClass<T>): Set<T> {
        return processedEvents
                .filterIsInstanceTo(mutableSetOf(), clazz.java)
    }

    override fun <T : TaskEvent> getSinglePastEventOfType(clazz: KClass<T>): T? {
        val events = getPastEventsOfType(clazz)

        return when (events.size) {
            0 -> null
            1 -> events.first()
            else -> throw IllegalStateException("Multiple events of type ${clazz.simpleName} found.")
        }
    }

    override fun commandForContainer(container: Container): String? {
        if (isTaskContainer(container) && graph.task.runConfiguration.command != null) {
            return graph.task.runConfiguration.command
        }

        return container.command
    }

    override fun isTaskContainer(container: Container) = container == graph.taskContainerNode.container
    override fun dependenciesOf(container: Container) = graph.nodeFor(container).dependsOnContainers
    override fun containersThatDependOn(container: Container) = graph.nodeFor(container).dependedOnByContainers
    override val allTaskContainers: Set<Container> by lazy { graph.allNodes.mapToSet() { it.container } }

    override var isAborting: Boolean = false
        private set

    override val projectName: String
        get() = graph.config.projectName
}
