/*
   Copyright 2017-2019 Charles Korn.

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
import batect.cli.options.defaultvalues.EnvironmentVariableDefaultValueProviderFactory
import batect.config.io.ConfigurationLoader
import batect.docker.DockerAPI
import batect.docker.DockerClient
import batect.docker.DockerContainerCreationRequestFactory
import batect.docker.DockerHostNameResolver
import batect.docker.DockerHttpConfig
import batect.docker.build.DockerIgnoreParser
import batect.docker.build.DockerImageBuildContextFactory
import batect.docker.build.DockerfileParser
import batect.docker.pull.DockerRegistryCredentialsConfigurationFile
import batect.docker.pull.DockerRegistryCredentialsProvider
import batect.docker.pull.DockerRegistryDomainResolver
import batect.docker.pull.DockerRegistryIndexResolver
import batect.docker.run.ContainerIOStreamer
import batect.docker.run.ContainerKiller
import batect.docker.run.ContainerTTYManager
import batect.docker.run.ContainerWaiter
import batect.execution.ContainerCommandResolver
import batect.execution.ContainerDependencyGraphProvider
import batect.execution.InterruptionTrap
import batect.execution.ParallelExecutionManagerProvider
import batect.execution.RunAsCurrentUserConfigurationProvider
import batect.execution.RunOptions
import batect.execution.TaskExecutionOrderResolver
import batect.execution.TaskRunner
import batect.execution.TaskStateMachineProvider
import batect.execution.TaskSuggester
import batect.execution.model.stages.CleanupStagePlanner
import batect.execution.model.stages.RunStagePlanner
import batect.execution.model.steps.TaskStepRunner
import batect.logging.ApplicationInfoLogger
import batect.logging.HttpLoggingInterceptor
import batect.logging.LogMessageWriter
import batect.logging.LoggerFactory
import batect.logging.StandardAdditionalDataSource
import batect.logging.singletonWithLogger
import batect.os.NativeMethods
import batect.os.PathResolverFactory
import batect.os.ProcessRunner
import batect.os.SignalListener
import batect.os.SystemInfo
import batect.os.proxies.ProxyEnvironmentVariablePreprocessor
import batect.os.proxies.ProxyEnvironmentVariablesProvider
import batect.os.unix.UnixNativeMethods
import batect.os.windows.WindowsNativeMethods
import batect.ui.Console
import batect.ui.ConsoleDimensions
import batect.ui.ConsoleInfo
import batect.ui.EventLoggerProvider
import batect.ui.FailureErrorMessageFormatter
import batect.ui.fancy.StartupProgressDisplayProvider
import batect.updates.UpdateInfoDownloader
import batect.updates.UpdateInfoStorage
import batect.updates.UpdateInfoUpdater
import batect.updates.UpdateNotifier
import jnr.ffi.Platform
import jnr.posix.POSIX
import jnr.posix.POSIXFactory
import okhttp3.OkHttpClient
import org.kodein.di.DKodein
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.singleton
import java.io.InputStream
import java.io.PrintStream
import java.nio.file.FileSystem
import java.nio.file.FileSystems

fun createKodeinConfiguration(outputStream: PrintStream, errorStream: PrintStream, inputStream: InputStream): DKodein = Kodein.direct {
    bind<FileSystem>() with singleton { FileSystems.getDefault() }
    bind<POSIX>() with singleton { POSIXFactory.getNativePOSIX() }
    bind<PrintStream>(StreamType.Error) with instance(errorStream)
    bind<PrintStream>(StreamType.Output) with instance(outputStream)
    bind<InputStream>(StreamType.Input) with instance(inputStream)

    bind<OkHttpClient>() with singleton {
        OkHttpClient.Builder()
            .addInterceptor(instance<HttpLoggingInterceptor>())
            .build()
    }

    import(cliModule)
    import(configModule)
    import(dockerModule)
    import(executionModule)
    import(loggingModule)
    import(osModule)
    import(uiModule)
    import(updatesModule)
    import(coreModule)

    if (Platform.getNativePlatform().os in setOf(Platform.OS.DARWIN, Platform.OS.LINUX)) {
        import(unixModule)
    }

    if (Platform.getNativePlatform().os == Platform.OS.WINDOWS) {
        import(windowsModule)
    }
}

enum class StreamType {
    Input,
    Output,
    Error
}

private val cliModule = Kodein.Module("cli") {
    bind<CommandFactory>() with singleton { CommandFactory() }
    bind<CommandLineOptionsParser>() with singleton { CommandLineOptionsParser(instance(), instance()) }
    bind<EnvironmentVariableDefaultValueProviderFactory>() with singleton { EnvironmentVariableDefaultValueProviderFactory() }

    bind<RunTaskCommand>() with singletonWithLogger { logger ->
        RunTaskCommand(
            commandLineOptions().configurationFileName,
            instance(),
            instance(),
            instance(),
            instance(),
            instance(),
            instance(),
            instance(StreamType.Output),
            instance(StreamType.Error),
            logger
        )
    }

    bind<HelpCommand>() with singleton { HelpCommand(instance(), instance(StreamType.Output)) }
    bind<ListTasksCommand>() with singleton { ListTasksCommand(commandLineOptions().configurationFileName, instance(), instance(StreamType.Output)) }
    bind<VersionInfoCommand>() with singleton { VersionInfoCommand(instance(), instance(StreamType.Output), instance(), instance(), instance()) }
    bind<UpgradeCommand>() with singletonWithLogger { logger -> UpgradeCommand(instance(), instance(), instance(), instance(), instance(StreamType.Output), instance(StreamType.Error), logger) }
}

private val configModule = Kodein.Module("config") {
    bind<ConfigurationLoader>() with singletonWithLogger { logger -> ConfigurationLoader(instance(), logger) }
    bind<PathResolverFactory>() with singleton { PathResolverFactory(instance()) }
}

private val dockerModule = Kodein.Module("docker") {
    bind<ContainerIOStreamer>() with singleton { ContainerIOStreamer(instance(StreamType.Output), instance(StreamType.Input)) }
    bind<ContainerKiller>() with singleton { ContainerKiller(instance(), instance()) }
    bind<ContainerTTYManager>() with singletonWithLogger { logger -> ContainerTTYManager(instance(), instance(), instance(), logger) }
    bind<ContainerWaiter>() with singleton { ContainerWaiter(instance()) }
    bind<DockerAPI>() with singletonWithLogger { logger -> DockerAPI(instance(), logger) }
    bind<DockerClient>() with singletonWithLogger { logger -> DockerClient(instance(), instance(), instance(), instance(), instance(), instance(), instance(), instance(), instance(), logger) }
    bind<DockerContainerCreationRequestFactory>() with singleton { DockerContainerCreationRequestFactory(instance(), instance()) }
    bind<DockerfileParser>() with singleton { DockerfileParser() }
    bind<DockerIgnoreParser>() with singleton { DockerIgnoreParser() }
    bind<DockerImageBuildContextFactory>() with singleton { DockerImageBuildContextFactory(instance()) }
    bind<DockerHostNameResolver>() with singleton { DockerHostNameResolver(instance(), instance()) }
    bind<DockerHttpConfig>() with singleton { DockerHttpConfig(instance(), commandLineOptions().dockerHost) }
    bind<DockerRegistryCredentialsConfigurationFile>() with singletonWithLogger { logger -> DockerRegistryCredentialsConfigurationFile(instance(), instance(), logger) }
    bind<DockerRegistryCredentialsProvider>() with singleton { DockerRegistryCredentialsProvider(instance(), instance(), instance()) }
    bind<DockerRegistryDomainResolver>() with singleton { DockerRegistryDomainResolver() }
    bind<DockerRegistryIndexResolver>() with singleton { DockerRegistryIndexResolver() }
}

private val executionModule = Kodein.Module("execution") {
    bind<CleanupStagePlanner>() with singletonWithLogger { logger -> CleanupStagePlanner(logger) }
    bind<ContainerCommandResolver>() with singleton { ContainerCommandResolver(instance()) }
    bind<ContainerDependencyGraphProvider>() with singletonWithLogger { logger -> ContainerDependencyGraphProvider(instance(), logger) }
    bind<InterruptionTrap>() with singleton { InterruptionTrap(instance()) }
    bind<ParallelExecutionManagerProvider>() with singleton { ParallelExecutionManagerProvider(instance(), instance()) }
    bind<RunAsCurrentUserConfigurationProvider>() with singleton { RunAsCurrentUserConfigurationProvider(instance(), instance()) }
    bind<RunOptions>() with singleton { RunOptions(commandLineOptions()) }
    bind<RunStagePlanner>() with singletonWithLogger { logger -> RunStagePlanner(logger) }
    bind<TaskRunner>() with singletonWithLogger { logger -> TaskRunner(instance(), instance(), instance(), instance(), instance(), logger) }
    bind<TaskExecutionOrderResolver>() with singletonWithLogger { logger -> TaskExecutionOrderResolver(instance(), logger) }
    bind<TaskStateMachineProvider>() with singleton { TaskStateMachineProvider(instance(), instance(), instance(), instance()) }
    bind<TaskStepRunner>() with singletonWithLogger { logger -> TaskStepRunner(instance(), instance(), instance(), instance(), logger) }
    bind<TaskSuggester>() with singleton { TaskSuggester() }
}

private val loggingModule = Kodein.Module("logging") {
    bind<ApplicationInfoLogger>() with singletonWithLogger { logger -> ApplicationInfoLogger(logger, instance(), instance(), instance()) }
    bind<HttpLoggingInterceptor>() with singletonWithLogger { logger -> HttpLoggingInterceptor(logger) }
    bind<LoggerFactory>() with singleton { LoggerFactory(instance()) }
    bind<LogMessageWriter>() with singleton { LogMessageWriter() }
    bind<StandardAdditionalDataSource>() with singleton { StandardAdditionalDataSource(instance()) }
}

private val osModule = Kodein.Module("os") {
    bind<ProcessRunner>() with singletonWithLogger { logger -> ProcessRunner(logger) }
    bind<ProxyEnvironmentVariablePreprocessor>() with singletonWithLogger { logger -> ProxyEnvironmentVariablePreprocessor(instance(), logger) }
    bind<ProxyEnvironmentVariablesProvider>() with singleton { ProxyEnvironmentVariablesProvider(instance()) }
    bind<SignalListener>() with singleton { SignalListener(instance()) }
    bind<SystemInfo>() with singleton { SystemInfo(instance()) }
}

private val unixModule = Kodein.Module("os.unix") {
    bind<NativeMethods>() with singleton { UnixNativeMethods(instance()) }
}

private val windowsModule = Kodein.Module("os.windows") {
    bind<NativeMethods>() with singleton { WindowsNativeMethods(instance()) }
}

private val uiModule = Kodein.Module("ui") {
    bind<EventLoggerProvider>() with singleton {
        EventLoggerProvider(
            instance(),
            instance(StreamType.Output),
            instance(StreamType.Error),
            instance(),
            instance(),
            commandLineOptions().requestedOutputStyle,
            commandLineOptions().disableColorOutput
        )
    }

    bind<Console>(StreamType.Output) with singleton { Console(instance(StreamType.Output), enableComplexOutput = !commandLineOptions().disableColorOutput, consoleDimensions = instance()) }
    bind<Console>(StreamType.Error) with singleton { Console(instance(StreamType.Error), enableComplexOutput = !commandLineOptions().disableColorOutput, consoleDimensions = instance()) }
    bind<ConsoleDimensions>() with singletonWithLogger { logger -> ConsoleDimensions(instance(), instance(), logger) }
    bind<ConsoleInfo>() with singletonWithLogger { logger -> ConsoleInfo(instance(), instance(), logger) }
    bind<FailureErrorMessageFormatter>() with singleton { FailureErrorMessageFormatter() }
    bind<StartupProgressDisplayProvider>() with singleton { StartupProgressDisplayProvider(instance()) }
}

private val updatesModule = Kodein.Module("updates") {
    bind<UpdateInfoDownloader>() with singletonWithLogger { logger -> UpdateInfoDownloader(instance(), logger) }
    bind<UpdateInfoStorage>() with singletonWithLogger { logger -> UpdateInfoStorage(instance(), instance(), logger) }
    bind<UpdateInfoUpdater>() with singletonWithLogger { logger -> UpdateInfoUpdater(instance(), instance(), logger) }
    bind<UpdateNotifier>() with singletonWithLogger { logger -> UpdateNotifier(commandLineOptions().disableUpdateNotification, instance(), instance(), instance(), instance(StreamType.Output), logger) }
}

private val coreModule = Kodein.Module("core") {
    bind<VersionInfo>() with singleton { VersionInfo() }
}

private fun DKodein.commandLineOptions(): CommandLineOptions = this.instance()
