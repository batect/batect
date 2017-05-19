package decompose

import com.github.salomonbrys.kodein.*
import decompose.config.io.ConfigurationLoader
import decompose.config.io.PathResolverFactory
import decompose.docker.DockerClient
import decompose.docker.DockerImageLabellingStrategy
import java.io.PrintStream
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        val status = Application().run(args, System.out, System.err)
        exitProcess(status)
    } catch (e: Throwable) {
        System.err.println("Fatal exception: ")
        e.printStackTrace(System.err)
        exitProcess(-1)
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
            return -1
        }
    }
}

private fun createDefaultKodeinConfiguration(): Kodein = Kodein {
    bind<ConfigurationLoader>() with provider { ConfigurationLoader(instance(), instance()) }
    bind<PathResolverFactory>() with provider { PathResolverFactory() }
    bind<FileSystem>() with provider { FileSystems.getDefault() }
    bind<TaskRunner>() with provider { TaskRunner(instance()) }
    bind<DockerClient>() with provider { DockerClient(instance(), instance(), instance()) }
    bind<DockerImageLabellingStrategy>() with provider { DockerImageLabellingStrategy() }
}
