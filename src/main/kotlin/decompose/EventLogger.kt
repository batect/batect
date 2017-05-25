package decompose

import decompose.config.Container
import java.io.PrintStream

class EventLogger(private val console: Console) {
    fun imageBuildStarted(container: Container) {
        console.withColor(ConsoleColor.White) {
            print("Building ")
            printBold(container.name)
            println("...")
        }
    }

    fun commandStarted(container: Container, command: String?) {
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
}
