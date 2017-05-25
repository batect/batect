package decompose

import decompose.config.Container
import java.io.PrintStream

class EventLogger(val outputStream: PrintStream) {
    fun imageBuildStarted(container: Container) {
        outputStream.println("${Emoji.Hammer}  Building '${container.name}'...")
    }

    fun commandStarted(container: Container, command: String?) {
        outputStream.println("${Emoji.Gear}  Running '$command' in '${container.name}'...")
    }
}
