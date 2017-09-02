package decompose

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.KodeinAware
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.github.salomonbrys.kodein.provider
import decompose.cli.CommandLineParser
import decompose.cli.DecomposeCommandLineParser
import decompose.cli.Failed
import decompose.cli.Succeeded
import decompose.config.io.ConfigurationLoader
import decompose.config.io.PathResolverFactory
import decompose.docker.DockerClient
import decompose.docker.DockerContainerCreationCommandGenerator
import decompose.docker.DockerImageLabellingStrategy
import decompose.docker.ProcessRunner
import decompose.model.DependencyGraphProvider
import decompose.model.TaskStateMachineProvider
import decompose.model.steps.TaskStepRunner
import java.io.PrintStream
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    try {
        val status = Application(System.out, System.err).run(args.toList())
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

    private val errorStream: PrintStream = instance(PrintStreamType.Error)
    private val commandLineParser: CommandLineParser = instance()

    fun run(args: Iterable<String>): Int {
        try {
            val result = commandLineParser.parse(args)

            return when (result) {
                is Failed -> {
                    errorStream.println(result.error)
                    -1
                }
                is Succeeded -> result.command.run()
            }
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
    bind<TaskRunner>() with provider { TaskRunner(instance(), instance(), instance(), instance()) }
    bind<DockerClient>() with provider { DockerClient(instance(), instance(), instance()) }
    bind<DockerImageLabellingStrategy>() with provider { DockerImageLabellingStrategy() }
    bind<ProcessRunner>() with provider { ProcessRunner() }
    bind<DockerContainerCreationCommandGenerator>() with provider { DockerContainerCreationCommandGenerator() }
    bind<EventLogger>() with provider { EventLogger(instance()) }
    bind<Console>() with provider { Console(instance(PrintStreamType.Output)) }
    bind<PrintStream>(PrintStreamType.Error) with instance(errorStream)
    bind<PrintStream>(PrintStreamType.Output) with instance(outputStream)
    bind<CommandLineParser>() with provider { DecomposeCommandLineParser(this) }
    bind<TaskStepRunner>() with provider { TaskStepRunner(instance()) }
    bind<DependencyGraphProvider>() with provider { DependencyGraphProvider() }
    bind<TaskStateMachineProvider>() with provider { TaskStateMachineProvider() }
}

