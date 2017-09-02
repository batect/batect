package decompose

import decompose.config.Container
import decompose.model.events.TaskEvent
import decompose.model.events.TaskEventSink
import decompose.model.steps.BuildImageStep
import decompose.model.steps.CreateContainerStep
import decompose.model.steps.DisplayTaskFailureStep
import decompose.model.steps.RunContainerStep
import decompose.model.steps.StartContainerStep
import decompose.model.steps.TaskStep

class EventLogger(private val console: Console) : TaskEventSink {
    private val commands = mutableMapOf<Container, String?>()

    fun reset() {
        commands.clear()
    }

    fun logTaskFailed(taskName: String) {
        console.withColor(ConsoleColor.Red) {
            print("The task ")
            printBold(taskName)
            println(" failed. See above for details.")
        }
    }

    override fun postEvent(event: TaskEvent) {

    }

    fun logBeforeStartingStep(step: TaskStep) {
        when (step) {
            is BuildImageStep -> logImageBuildStarting(step.container)
            is StartContainerStep -> logDependencyContainerStarting(step.container)
            is RunContainerStep -> logCommandStarting(step.container, commands[step.container])
            is DisplayTaskFailureStep -> logTaskFailure(step.message)
            is CreateContainerStep -> commands[step.container] = step.command
        }
    }

    private fun logImageBuildStarting(container: Container) {
        console.withColor(ConsoleColor.White) {
            print("Building ")
            printBold(container.name)
            println("...")
        }
    }

    private fun logCommandStarting(container: Container, command: String?) {
        console.withColor(ConsoleColor.White) {
            print("Running ")

            if (command != null) {
                printBold(command)
                print(" in ")
            }

            printBold(container.name)
            println("...")
        }
    }

    private fun logDependencyContainerStarting(dependency: Container) {
        console.withColor(ConsoleColor.White) {
            print("Starting dependency ")
            printBold(dependency.name)
            println("...")
        }
    }

    private fun logTaskFailure(message: String) {
        console.withColor(ConsoleColor.Red) {
            println(message)
        }
    }
}
