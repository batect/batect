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

package batect

import batect.cli.BatectCommandLineParser
import batect.cli.CommandLineParser
import batect.cli.CommandLineParsingResult
import batect.cli.CommonOptions
import batect.config.io.ConfigurationLoader
import batect.config.io.PathResolverFactory
import batect.docker.DockerClient
import batect.docker.DockerContainerCreationCommandGenerator
import batect.docker.DockerImageLabellingStrategy
import batect.logging.ApplicationInfoLogger
import batect.logging.LogMessageWriter
import batect.logging.LoggerFactory
import batect.logging.StandardAdditionalDataSource
import batect.logging.singletonWithLogger
import batect.model.DependencyGraphProvider
import batect.model.TaskExecutionOrderResolver
import batect.model.TaskStateMachineProvider
import batect.model.steps.TaskStepRunner
import batect.os.ProcessRunner
import batect.os.SystemInfo
import batect.ui.Console
import batect.ui.ConsoleInfo
import batect.ui.EventLoggerProvider
import batect.ui.StartupProgressDisplayProvider
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.KodeinAware
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.github.salomonbrys.kodein.singleton
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
            val result = commandLineParser.parse(args) { kodein ->
                kodein.instance<ApplicationInfoLogger>().logApplicationInfo(args)
            }

            return when (result) {
                is CommandLineParsingResult.Failed -> {
                    errorStream.println(result.error)
                    -1
                }
                is CommandLineParsingResult.Succeeded -> result.command.run()
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
    bind<ConfigurationLoader>() with singletonWithLogger { logger -> ConfigurationLoader(instance(), instance(), logger) }
    bind<PathResolverFactory>() with singleton { PathResolverFactory() }
    bind<FileSystem>() with singleton { FileSystems.getDefault() }
    bind<TaskRunner>() with singletonWithLogger { logger -> TaskRunner(instance(), instance(), instance(), instance(), logger) }
    bind<DockerClient>() with singletonWithLogger { logger -> DockerClient(instance(), instance(), instance(), instance(), logger) }
    bind<DockerImageLabellingStrategy>() with singleton { DockerImageLabellingStrategy() }
    bind<ProcessRunner>() with singletonWithLogger { logger -> ProcessRunner(logger) }
    bind<DockerContainerCreationCommandGenerator>() with singleton { DockerContainerCreationCommandGenerator() }
    bind<EventLoggerProvider>() with singleton { EventLoggerProvider(instance(PrintStreamType.Output), instance(PrintStreamType.Error), instance(), instance(), instance(CommonOptions.ForceSimpleOutputMode)) }
    bind<Console>(PrintStreamType.Output) with singleton { Console(instance(PrintStreamType.Output)) }
    bind<Console>(PrintStreamType.Error) with singleton { Console(instance(PrintStreamType.Error)) }
    bind<PrintStream>(PrintStreamType.Error) with instance(errorStream)
    bind<PrintStream>(PrintStreamType.Output) with instance(outputStream)
    bind<CommandLineParser>() with singleton { BatectCommandLineParser(this) }
    bind<TaskStepRunner>() with singletonWithLogger { logger -> TaskStepRunner(instance(), logger) }
    bind<DependencyGraphProvider>() with singletonWithLogger { logger -> DependencyGraphProvider(logger) }
    bind<TaskStateMachineProvider>() with singleton { TaskStateMachineProvider(instance()) }
    bind<ParallelExecutionManagerProvider>() with singleton { ParallelExecutionManagerProvider(instance()) }
    bind<StartupProgressDisplayProvider>() with singleton { StartupProgressDisplayProvider() }
    bind<TaskExecutionOrderResolver>() with singletonWithLogger { logger -> TaskExecutionOrderResolver(logger) }
    bind<ConsoleInfo>() with singletonWithLogger { logger -> ConsoleInfo(instance(), System.getenv(), logger) }
    bind<VersionInfo>() with singleton { VersionInfo() }
    bind<SystemInfo>() with singleton { SystemInfo(System.getProperties()) }
    bind<LoggerFactory>() with singleton { LoggerFactory(instance()) }
    bind<LogMessageWriter>() with singleton { LogMessageWriter() }
    bind<StandardAdditionalDataSource>() with singleton { StandardAdditionalDataSource() }
    bind<ApplicationInfoLogger>() with singletonWithLogger { logger -> ApplicationInfoLogger(logger, instance(), instance(), instance(), System.getenv()) }
}
