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
import batect.cli.commands.CommandFactory
import batect.cli.commands.HelpCommand
import batect.cli.commands.ListTasksCommand
import batect.cli.commands.RunTaskCommand
import batect.cli.commands.UpgradeCommand
import batect.cli.commands.VersionInfoCommand
import batect.config.io.ConfigurationLoader
import batect.config.io.PathResolverFactory
import batect.docker.DockerAPI
import batect.docker.DockerClient
import batect.docker.DockerContainerCreationRequestFactory
import batect.docker.DockerHostNameResolver
import batect.docker.DockerHttpConfig
import batect.execution.ContainerCommandResolver
import batect.execution.ContainerDependencyGraphProvider
import batect.execution.ParallelExecutionManagerProvider
import batect.execution.RunAsCurrentUserConfigurationProvider
import batect.execution.RunOptions
import batect.execution.TaskExecutionOrderResolver
import batect.execution.TaskRunner
import batect.execution.TaskStateMachineProvider
import batect.execution.model.stages.CleanupStagePlanner
import batect.execution.model.stages.RunStagePlanner
import batect.execution.model.steps.TaskStepRunner
import batect.logging.ApplicationInfoLogger
import batect.logging.LogMessageWriter
import batect.logging.LoggerFactory
import batect.logging.StandardAdditionalDataSource
import batect.logging.singletonWithLogger
import batect.os.NativeMethods
import batect.os.ProcessRunner
import batect.os.SystemInfo
import batect.os.proxies.ProxyEnvironmentVariablePreprocessor
import batect.os.proxies.ProxyEnvironmentVariablesProvider
import batect.ui.Console
import batect.ui.ConsoleInfo
import batect.ui.EventLoggerProvider
import batect.ui.FailureErrorMessageFormatter
import batect.ui.fancy.StartupProgressDisplayProvider
import batect.updates.UpdateInfoDownloader
import batect.updates.UpdateInfoStorage
import batect.updates.UpdateInfoUpdater
import batect.updates.UpdateNotifier
import jnr.posix.POSIX
import jnr.posix.POSIXFactory
import okhttp3.OkHttpClient
import org.kodein.di.DKodein
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.singleton
import java.io.PrintStream
import java.nio.file.FileSystem
import java.nio.file.FileSystems

fun createKodeinConfiguration(outputStream: PrintStream, errorStream: PrintStream): DKodein = Kodein.direct {
    bind<FileSystem>() with singleton { FileSystems.getDefault() }
    bind<OkHttpClient>() with singleton { OkHttpClient.Builder().build() }
    bind<POSIX>() with singleton { POSIXFactory.getNativePOSIX() }
    bind<PrintStream>(PrintStreamType.Error) with instance(errorStream)
    bind<PrintStream>(PrintStreamType.Output) with instance(outputStream)

    import(cliModule)
    import(configModule)
    import(dockerModule)
    import(executionModule)
    import(loggingModule)
    import(osModule)
    import(uiModule)
    import(updatesModule)
    import(coreModule)
}

enum class PrintStreamType {
    Output,
    Error
}

private val cliModule = Kodein.Module("cli") {
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

private val configModule = Kodein.Module("config") {
    bind<ConfigurationLoader>() with singletonWithLogger { logger -> ConfigurationLoader(instance(), instance(), logger) }
    bind<PathResolverFactory>() with singleton { PathResolverFactory() }
}

private val dockerModule = Kodein.Module("docker") {
    bind<DockerAPI>() with singletonWithLogger { logger -> DockerAPI(instance(), logger) }
    bind<DockerClient>() with singletonWithLogger { logger -> DockerClient(instance(), instance(), instance(), logger) }
    bind<DockerContainerCreationRequestFactory>() with singleton { DockerContainerCreationRequestFactory(instance(), instance()) }
    bind<DockerHostNameResolver>() with singleton { DockerHostNameResolver(instance(), instance()) }
    bind<DockerHttpConfig>() with singleton { DockerHttpConfig(instance()) }
}

private val executionModule = Kodein.Module("execution") {
    bind<CleanupStagePlanner>() with singletonWithLogger { logger -> CleanupStagePlanner(logger) }
    bind<ContainerCommandResolver>() with singleton { ContainerCommandResolver(instance()) }
    bind<ContainerDependencyGraphProvider>() with singletonWithLogger { logger -> ContainerDependencyGraphProvider(instance(), logger) }
    bind<ParallelExecutionManagerProvider>() with singleton { ParallelExecutionManagerProvider(instance(), instance()) }
    bind<RunAsCurrentUserConfigurationProvider>() with singleton { RunAsCurrentUserConfigurationProvider(instance(), instance()) }
    bind<RunOptions>() with singleton { RunOptions(commandLineOptions()) }
    bind<RunStagePlanner>() with singletonWithLogger { logger -> RunStagePlanner(logger) }
    bind<TaskRunner>() with singletonWithLogger { logger -> TaskRunner(instance(), instance(), instance(), instance(), logger) }
    bind<TaskExecutionOrderResolver>() with singletonWithLogger { logger -> TaskExecutionOrderResolver(logger) }
    bind<TaskStateMachineProvider>() with singleton { TaskStateMachineProvider(instance(), instance(), instance(), instance()) }
    bind<TaskStepRunner>() with singletonWithLogger { logger -> TaskStepRunner(instance(), instance(), instance(), instance(), logger) }
}

private val loggingModule = Kodein.Module("logging") {
    bind<ApplicationInfoLogger>() with singletonWithLogger { logger -> ApplicationInfoLogger(logger, instance(), instance(), instance()) }
    bind<LoggerFactory>() with singleton { LoggerFactory(instance()) }
    bind<LogMessageWriter>() with singleton { LogMessageWriter() }
    bind<StandardAdditionalDataSource>() with singleton { StandardAdditionalDataSource(instance()) }
}

private val osModule = Kodein.Module("os") {
    bind<NativeMethods>() with singleton { NativeMethods(instance()) }
    bind<ProcessRunner>() with singletonWithLogger { logger -> ProcessRunner(logger) }
    bind<ProxyEnvironmentVariablePreprocessor>() with singletonWithLogger { logger -> ProxyEnvironmentVariablePreprocessor(instance(), logger) }
    bind<ProxyEnvironmentVariablesProvider>() with singleton { ProxyEnvironmentVariablesProvider(instance()) }
    bind<SystemInfo>() with singleton { SystemInfo(instance()) }
}

private val uiModule = Kodein.Module("ui") {
    bind<EventLoggerProvider>() with singleton {
        EventLoggerProvider(
            instance(),
            instance(PrintStreamType.Output),
            instance(PrintStreamType.Error),
            instance(),
            instance(),
            commandLineOptions().requestedOutputStyle,
            commandLineOptions().disableColorOutput
        )
    }

    bind<Console>(PrintStreamType.Output) with singleton { Console(instance(PrintStreamType.Output), enableComplexOutput = !commandLineOptions().disableColorOutput, consoleInfo = instance()) }
    bind<Console>(PrintStreamType.Error) with singleton { Console(instance(PrintStreamType.Error), enableComplexOutput = !commandLineOptions().disableColorOutput, consoleInfo = instance()) }
    bind<ConsoleInfo>() with singletonWithLogger { logger -> ConsoleInfo(instance(), instance(), logger) }
    bind<FailureErrorMessageFormatter>() with singleton { FailureErrorMessageFormatter() }
    bind<StartupProgressDisplayProvider>() with singleton { StartupProgressDisplayProvider() }
}

private val updatesModule = Kodein.Module("updates") {
    bind<UpdateInfoDownloader>() with singletonWithLogger { logger -> UpdateInfoDownloader(instance(), logger) }
    bind<UpdateInfoStorage>() with singletonWithLogger { logger -> UpdateInfoStorage(instance(), instance(), logger) }
    bind<UpdateInfoUpdater>() with singletonWithLogger { logger -> UpdateInfoUpdater(instance(), instance(), logger) }
    bind<UpdateNotifier>() with singletonWithLogger { logger -> UpdateNotifier(commandLineOptions().disableUpdateNotification, instance(), instance(), instance(), instance(PrintStreamType.Output), logger) }
}

private val coreModule = Kodein.Module("core") {
    bind<VersionInfo>() with singleton { VersionInfo() }
}

private fun DKodein.commandLineOptions(): CommandLineOptions = this.instance()
