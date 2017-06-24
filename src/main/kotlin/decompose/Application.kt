package decompose

import com.github.salomonbrys.kodein.*
import decompose.config.io.ConfigurationLoader
import decompose.config.io.PathResolverFactory
import decompose.docker.DockerClient
import decompose.docker.DockerContainerCreationCommandGenerator
import decompose.docker.DockerImageLabellingStrategy
import decompose.docker.ProcessRunner
import java.io.PrintStream
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        val status = Application(System.out, System.err).run(args)
        exitProcess(status)
    } catch (e: Throwable) {
        System.err.println("Fatal exception: ")
        e.printStackTrace(System.err)
        exitProcess(-1)
    }
}

class Application(override val kodein: Kodein) : KodeinAware {
    constructor(outputStream: PrintStream, errorStream: PrintStream) :
            this(createDefaultKodeinConfiguration(outputStream, errorStream))

    private val configLoader: ConfigurationLoader = instance()
    private val taskRunner: TaskRunner = instance()
    private val errorStream: PrintStream = instance(PrintStreamType.Error)

    fun run(args: Array<String>): Int {
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

enum class PrintStreamType {
    Output,
    Error
}

private fun createDefaultKodeinConfiguration(outputStream: PrintStream, errorStream: PrintStream): Kodein = Kodein {
    bind<ConfigurationLoader>() with provider { ConfigurationLoader(instance(), instance()) }
    bind<PathResolverFactory>() with provider { PathResolverFactory() }
    bind<FileSystem>() with provider { FileSystems.getDefault() }
    bind<TaskRunner>() with provider { TaskRunner(instance(), instance(), instance()) }
    bind<DockerClient>() with provider { DockerClient(instance(), instance(), instance()) }
    bind<DockerImageLabellingStrategy>() with provider { DockerImageLabellingStrategy() }
    bind<ProcessRunner>() with provider { ProcessRunner() }
    bind<DockerContainerCreationCommandGenerator>() with provider { DockerContainerCreationCommandGenerator() }
    bind<EventLogger>() with provider { EventLogger(instance()) }
    bind<Console>() with provider { Console(instance(PrintStreamType.Output)) }
    bind<DependencyResolver>() with provider { DependencyResolver() }
    bind<PrintStream>(PrintStreamType.Error) with instance(errorStream)
    bind<PrintStream>(PrintStreamType.Output) with instance(outputStream)
    bind<DependencyRuntimeManagerFactory>() with provider { DependencyRuntimeManagerFactory(instance(), instance(), instance()) }
}

