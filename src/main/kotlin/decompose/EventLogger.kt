package decompose

import decompose.config.Container
import decompose.model.events.TaskEvent
import decompose.model.events.TaskEventSink
import decompose.model.steps.TaskStep

class EventLogger(private val console: Console) : TaskEventSink {
    fun logBeforeStartingStep(step: TaskStep) {

    }

    override fun postEvent(event: TaskEvent) {

    }

    fun imageBuildStarting(container: Container) {
        console.withColor(ConsoleColor.White) {
            print("Building ")
            printBold(container.name)
            println("...")
        }
    }

    fun commandStarting(container: Container, command: String?) {
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

    fun dependencyStarting(dependency: Container) {
        console.withColor(ConsoleColor.White) {
            print("Starting dependency ")
            printBold(dependency.name)
            println("...")
        }
    }

    fun taskFailed(taskName: String) {
        TODO()
    }
}
