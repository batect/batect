/*
   Copyright 2017-2018 Charles Korn.

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

import batect.cli.CommandLineOptions
import batect.cli.CommandLineOptionsParser
import batect.cli.CommandLineOptionsParsingResult
import batect.cli.commands.CommandFactory
import batect.cli.commands.HelpCommand
import batect.cli.commands.ListTasksCommand
import batect.cli.commands.RunTaskCommand
import batect.cli.commands.UpgradeCommand
import batect.cli.commands.VersionInfoCommand
import batect.config.io.ConfigurationLoader
import batect.config.io.PathResolverFactory
import batect.docker.DockerClient
import batect.docker.DockerContainerCreationCommandGenerator
import batect.docker.DockerContainerCreationRequestFactory
import batect.docker.DockerImageLabellingStrategy
import batect.logging.ApplicationInfoLogger
import batect.logging.LogMessageWriter
import batect.logging.LoggerFactory
import batect.logging.StandardAdditionalDataSource
import batect.logging.singletonWithLogger
import batect.model.ContainerCommandResolver
import batect.model.DependencyGraphProvider
import batect.model.RunAsCurrentUserConfigurationProvider
import batect.model.RunOptions
import batect.model.TaskExecutionOrderResolver
import batect.model.TaskStateMachineProvider
import batect.model.steps.TaskStepRunner
import batect.os.ProcessRunner
import batect.os.ProxyEnvironmentVariablesProvider
import batect.os.SystemInfo
import batect.ui.Console
import batect.ui.ConsoleInfo
import batect.ui.EventLoggerProvider
import batect.ui.fancy.StartupProgressDisplayProvider
import batect.updates.UpdateInfoDownloader
import batect.updates.UpdateInfoStorage
import batect.updates.UpdateInfoUpdater
import batect.updates.UpdateNotifier
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.KodeinAware
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.github.salomonbrys.kodein.singleton
import okhttp3.OkHttpClient
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
    private val commandLineOptionsParser: CommandLineOptionsParser = instance()
    private val commandFactory: CommandFactory = instance()

    fun run(args: Iterable<String>): Int {
        try {
            val result = commandLineOptionsParser.parse(args)

            when (result) {
                is CommandLineOptionsParsingResult.Failed -> {
                    errorStream.println(result.message)
                    return -1
                }
                is CommandLineOptionsParsingResult.Succeeded -> {
                    val extendedKodein = result.options.extend(kodein)

                    val applicationInfoLogger = extendedKodein.instance<ApplicationInfoLogger>()
                    applicationInfoLogger.logApplicationInfo(args)

                    val command = commandFactory.createCommand(result.options, extendedKodein)
                    return command.run()
                }
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

private val cliModule = Kodein.Module {
    bind<CommandFactory>() with singleton { CommandFactory() }
    bind<CommandLineOptionsParser>() with singleton { CommandLineOptionsParser() }

    bind<RunTaskCommand>() with singletonWithLogger { logger ->
        RunTaskCommand(
            commandLineOptions().configurationFileName,
            instance(),
            instance(),
            instance(),
            instance(),
            instance(),
            instance(),
            instance(PrintStreamType.Output),
            instance(PrintStreamType.Error),
            logger
        )
    }

    bind<HelpCommand>() with singleton { HelpCommand(instance(), instance(PrintStreamType.Output)) }
    bind<ListTasksCommand>() with singleton { ListTasksCommand(commandLineOptions().configurationFileName, instance(), instance(PrintStreamType.Output)) }
    bind<VersionInfoCommand>() with singleton { VersionInfoCommand(instance(), instance(PrintStreamType.Output), instance(), instance(), instance()) }
    bind<UpgradeCommand>() with singletonWithLogger { logger -> UpgradeCommand(instance(), instance(), instance(), instance(), instance(PrintStreamType.Output), instance(PrintStreamType.Error), logger) }
}

private val configModule = Kodein.Module {
    bind<ConfigurationLoader>() with singletonWithLogger { logger -> ConfigurationLoader(instance(), instance(), logger) }
    bind<PathResolverFactory>() with singleton { PathResolverFactory() }
}

private val dockerModule = Kodein.Module {
    bind<DockerClient>() with singletonWithLogger { logger -> DockerClient(instance(), instance(), instance(), instance(), logger) }
    bind<DockerContainerCreationCommandGenerator>() with singleton { DockerContainerCreationCommandGenerator() }
    bind<DockerContainerCreationRequestFactory>() with singleton { DockerContainerCreationRequestFactory(instance(), instance()) }
    bind<DockerImageLabellingStrategy>() with singleton { DockerImageLabellingStrategy() }
}

private val loggingModule = Kodein.Module {
    bind<ApplicationInfoLogger>() with singletonWithLogger { logger -> ApplicationInfoLogger(logger, instance(), instance(), instance()) }
    bind<LoggerFactory>() with singleton { LoggerFactory(instance()) }
    bind<LogMessageWriter>() with singleton { LogMessageWriter() }
    bind<StandardAdditionalDataSource>() with singleton { StandardAdditionalDataSource() }
}

private val modelModule = Kodein.Module {
    bind<ContainerCommandResolver>() with singleton { ContainerCommandResolver(instance()) }
    bind<DependencyGraphProvider>() with singletonWithLogger { logger -> DependencyGraphProvider(instance(), logger) }
    bind<RunAsCurrentUserConfigurationProvider>() with singleton { RunAsCurrentUserConfigurationProvider(instance(), instance()) }
    bind<RunOptions>() with singleton { RunOptions(commandLineOptions()) }
    bind<TaskExecutionOrderResolver>() with singletonWithLogger { logger -> TaskExecutionOrderResolver(logger) }
    bind<TaskStateMachineProvider>() with singleton { TaskStateMachineProvider(instance()) }
    bind<TaskStepRunner>() with singletonWithLogger { logger -> TaskStepRunner(instance(), instance(), instance(), instance(), logger) }
}

private val osModule = Kodein.Module {
    bind<ProcessRunner>() with singletonWithLogger { logger -> ProcessRunner(logger) }
    bind<ProxyEnvironmentVariablesProvider>() with singleton { ProxyEnvironmentVariablesProvider() }
    bind<SystemInfo>() with singleton { SystemInfo(instance()) }
}

private val uiModule = Kodein.Module {
    bind<EventLoggerProvider>() with singleton {
        EventLoggerProvider(
            instance(PrintStreamType.Output),
            instance(PrintStreamType.Error),
            instance(),
            instance(),
            commandLineOptions().forceSimpleOutputMode,
            commandLineOptions().forceQuietOutputMode
        )
    }

    bind<Console>(PrintStreamType.Output) with singleton { Console(instance(PrintStreamType.Output), enableComplexOutput = !commandLineOptions().disableColorOutput, consoleInfo = instance()) }
    bind<Console>(PrintStreamType.Error) with singleton { Console(instance(PrintStreamType.Error), enableComplexOutput = !commandLineOptions().disableColorOutput, consoleInfo = instance()) }
    bind<ConsoleInfo>() with singletonWithLogger { logger -> ConsoleInfo(instance(), logger) }
    bind<StartupProgressDisplayProvider>() with singleton { StartupProgressDisplayProvider() }
}

private val updatesModule = Kodein.Module {
    bind<UpdateInfoDownloader>() with singletonWithLogger { logger -> UpdateInfoDownloader(instance(), logger) }
    bind<UpdateInfoStorage>() with singletonWithLogger { logger -> UpdateInfoStorage(instance(), instance(), logger) }
    bind<UpdateInfoUpdater>() with singletonWithLogger { logger -> UpdateInfoUpdater(instance(), instance(), logger) }
    bind<UpdateNotifier>() with singletonWithLogger { logger -> UpdateNotifier(commandLineOptions().disableUpdateNotification, instance(), instance(), instance(), instance(PrintStreamType.Output), logger) }
}

private val coreModule = Kodein.Module {
    bind<ParallelExecutionManagerProvider>() with singleton { ParallelExecutionManagerProvider(instance(), instance()) }
    bind<TaskRunner>() with singletonWithLogger { logger -> TaskRunner(instance(), instance(), instance(), instance(), logger) }
    bind<VersionInfo>() with singleton { VersionInfo() }
}

private fun createDefaultKodeinConfiguration(outputStream: PrintStream, errorStream: PrintStream): Kodein = Kodein {
    bind<FileSystem>() with singleton { FileSystems.getDefault() }
    bind<OkHttpClient>() with singleton { OkHttpClient.Builder().build() }
    bind<PrintStream>(PrintStreamType.Error) with instance(errorStream)
    bind<PrintStream>(PrintStreamType.Output) with instance(outputStream)

    import(cliModule)
    import(configModule)
    import(dockerModule)
    import(loggingModule)
    import(modelModule)
    import(osModule)
    import(uiModule)
    import(updatesModule)
    import(coreModule)
}

private fun Kodein.commandLineOptions(): CommandLineOptions = this.instance()
