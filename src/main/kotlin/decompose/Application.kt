package decompose

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.KodeinAware
import com.github.salomonbrys.kodein.instance
import decompose.config.io.ConfigurationLoader
import java.io.PrintStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        val status = Application().run(args, System.out, System.err)
        exitProcess(status)
    } catch (e: Throwable) {
        System.err.println("Fatal exception: ")
        e.printStackTrace(System.err)
    }
}

class Application(override val kodein: Kodein) : KodeinAware {
    constructor() : this(createDefaultKodeinConfiguration())

    private val configLoader: ConfigurationLoader = instance()
    private val taskRunner: TaskRunner = instance()

    fun run(args: Array<String>, outputStream: PrintStream, errorStream: PrintStream): Int {
        try {
            if (args.size != 2) {
                errorStream.println("Usage: decompose [configuration file] [task name]")
                return -1
            }

            val configFileName = args[0]
            val taskName = args[1]
            val config = configLoader.loadConfig(configFileName)

            return taskRunner.run(config, taskName)

        } catch (e: Throwable) {
            errorStream.println(e)
            return -2
        }
    }
}

private fun createDefaultKodeinConfiguration(): Kodein = Kodein {

}
