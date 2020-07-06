/*
   Copyright 2017-2020 Charles Korn.

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

package batect.ioc

import batect.VersionInfo
import batect.cli.CommandLineOptions
import batect.cli.CommandLineOptionsParser
import batect.cli.commands.CleanupCachesCommand
import batect.cli.commands.CommandFactory
import batect.cli.commands.DockerConnectivity
import batect.cli.commands.HelpCommand
import batect.cli.commands.ListTasksCommand
import batect.cli.commands.RunTaskCommand
import batect.cli.commands.UpgradeCommand
import batect.cli.commands.VersionInfoCommand
import batect.cli.options.defaultvalues.EnvironmentVariableDefaultValueProviderFactory
import batect.config.ProjectPaths
import batect.config.io.ConfigurationLoader
import batect.docker.DockerContainerCreationRequestFactory
import batect.docker.DockerContainerEnvironmentVariableProvider
import batect.docker.DockerContainerNameGenerator
import batect.docker.DockerHostNameResolver
import batect.docker.DockerHttpConfig
import batect.docker.DockerHttpConfigDefaults
import batect.docker.DockerTLSConfig
import batect.docker.api.ContainersAPI
import batect.docker.api.ExecAPI
import batect.docker.api.ImagesAPI
import batect.docker.api.NetworksAPI
import batect.docker.api.SystemInfoAPI
import batect.docker.api.VolumesAPI
import batect.docker.build.DockerIgnoreParser
import batect.docker.build.DockerImageBuildContextFactory
import batect.docker.build.DockerfileParser
import batect.docker.client.DockerClient
import batect.docker.client.DockerContainersClient
import batect.docker.client.DockerExecClient
import batect.docker.client.DockerImagesClient
import batect.docker.client.DockerNetworksClient
import batect.docker.client.DockerSystemInfoClient
import batect.docker.client.DockerVolumesClient
import batect.docker.pull.DockerRegistryCredentialsConfigurationFile
import batect.docker.pull.DockerRegistryCredentialsProvider
import batect.docker.pull.DockerRegistryDomainResolver
import batect.docker.pull.DockerRegistryIndexResolver
import batect.docker.run.ContainerIOStreamer
import batect.docker.run.ContainerTTYManager
import batect.docker.run.ContainerWaiter
import batect.execution.CacheManager
import batect.primitives.CancellationContext
import batect.execution.ConfigVariablesProvider
import batect.execution.ContainerCommandResolver
import batect.execution.ContainerDependencyGraph
import batect.execution.ContainerDependencyGraphProvider
import batect.execution.ContainerEntrypointResolver
import batect.execution.InterruptionTrap
import batect.execution.ParallelExecutionManager
import batect.execution.RunAsCurrentUserConfigurationProvider
import batect.execution.RunOptions
import batect.execution.TaskExecutionOrderResolver
import batect.execution.TaskRunner
import batect.execution.TaskStateMachine
import batect.execution.TaskSuggester
import batect.execution.VolumeMountResolver
import batect.execution.model.stages.CleanupStagePlanner
import batect.execution.model.stages.RunStagePlanner
import batect.execution.model.steps.TaskStepRunner
import batect.execution.model.steps.runners.BuildImageStepRunner
import batect.execution.model.steps.runners.CreateContainerStepRunner
import batect.execution.model.steps.runners.DeleteTaskNetworkStepRunner
import batect.execution.model.steps.runners.DeleteTemporaryDirectoryStepRunner
import batect.execution.model.steps.runners.DeleteTemporaryFileStepRunner
import batect.execution.model.steps.runners.InitialiseCachesStepRunner
import batect.execution.model.steps.runners.PrepareTaskNetworkStepRunner
import batect.execution.model.steps.runners.PullImageStepRunner
import batect.execution.model.steps.runners.RemoveContainerStepRunner
import batect.execution.model.steps.runners.RunContainerSetupCommandsStepRunner
import batect.execution.model.steps.runners.RunContainerStepRunner
import batect.execution.model.steps.runners.StopContainerStepRunner
import batect.execution.model.steps.runners.WaitForContainerToBecomeHealthyStepRunner
import batect.git.GitClient
import batect.io.ApplicationPaths
import batect.logging.ApplicationInfoLogger
import batect.logging.HttpLoggingInterceptor
import batect.logging.LogMessageWriter
import batect.logging.LoggerFactory
import batect.logging.StandardAdditionalDataSource
import batect.logging.singletonWithLogger
import batect.os.ConsoleDimensions
import batect.os.ConsoleInfo
import batect.os.ConsoleManager
import batect.os.HostEnvironmentVariables
import batect.os.NativeMethods
import batect.os.PathResolverFactory
import batect.os.ProcessRunner
import batect.os.SignalListener
import batect.os.SystemInfo
import batect.os.unix.ApplicationResolver
import batect.os.unix.UnixConsoleManager
import batect.os.unix.UnixNativeMethods
import batect.os.windows.WindowsConsoleManager
import batect.os.windows.WindowsNativeMethods
import batect.proxies.ProxyEnvironmentVariablePreprocessor
import batect.proxies.ProxyEnvironmentVariablesProvider
import batect.ui.Console
import batect.ui.EventLogger
import batect.ui.EventLoggerProvider
import batect.ui.FailureErrorMessageFormatter
import batect.ui.containerio.ContainerIOStreamingOptions
import batect.ui.fancy.StartupProgressDisplayProvider
import batect.updates.UpdateInfoDownloader
import batect.updates.UpdateInfoStorage
import batect.updates.UpdateInfoUpdater
import batect.updates.UpdateNotifier
import batect.utils.Json
import batect.wrapper.WrapperCache
import batect.wrapper.WrapperCacheCleanupTask
import com.hypirion.io.RevivableInputStream
import jnr.ffi.Platform
import jnr.posix.POSIX
import jnr.posix.POSIXFactory
import okhttp3.OkHttpClient
import org.kodein.di.DKodein
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.scoped
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
    bind<RevivableInputStream>(StreamType.Input) with instance(RevivableInputStream(inputStream))

    bind<OkHttpClient>() with singleton {
        OkHttpClient.Builder()
            .addInterceptor(instance<HttpLoggingInterceptor>())
            .build()
    }

    import(cliModule)
    import(configModule)
    import(dockerModule)
    import(gitModule)
    import(ioModule)
    import(iocModule)
    import(executionModule)
    import(loggingModule)
    import(osModule)
    import(proxiesModule)
    import(uiModule)
    import(updatesModule)
    import(wrapperModule)
    import(coreModule)

    if (Platform.getNativePlatform().os in setOf(Platform.OS.DARWIN, Platform.OS.LINUX)) {
        import(unixModule)
    }

    if (Platform.getNativePlatform().os == Platform.OS.WINDOWS) {
        import(windowsModule)
    }
}

private val cliModule = Kodein.Module("cli") {
    bind<CommandFactory>() with singleton { CommandFactory() }
    bind<CommandLineOptionsParser>() with singleton { CommandLineOptionsParser(instance(), instance(), instance(), instance()) }
    bind<EnvironmentVariableDefaultValueProviderFactory>() with singleton { EnvironmentVariableDefaultValueProviderFactory(instance()) }

    bind<RunTaskCommand>() with singletonWithLogger { logger ->
        RunTaskCommand(
            commandLineOptions().configurationFileName,
            instance(RunOptionsType.Overall),
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

    bind<DockerConnectivity>() with singleton { DockerConnectivity(instance(), instance(), instance(StreamType.Error)) }
    bind<CleanupCachesCommand>() with singleton { CleanupCachesCommand(instance(), instance(), instance(), instance(StreamType.Output)) }
    bind<HelpCommand>() with singleton { HelpCommand(instance(), instance(StreamType.Output), instance()) }
    bind<ListTasksCommand>() with singleton { ListTasksCommand(commandLineOptions().configurationFileName, instance(), instance(StreamType.Output)) }
    bind<VersionInfoCommand>() with singleton { VersionInfoCommand(instance(), instance(StreamType.Output), instance(), instance(), instance(), instance()) }
    bind<UpgradeCommand>() with singletonWithLogger { logger -> UpgradeCommand(instance(), instance(), instance(), instance(), instance(StreamType.Output), instance(StreamType.Error), instance(), logger) }
}

private val configModule = Kodein.Module("config") {
    bind<ConfigurationLoader>() with singletonWithLogger { logger -> ConfigurationLoader(instance(), logger) }
    bind<PathResolverFactory>() with singleton { PathResolverFactory(instance()) }
    bind<ProjectPaths>() with singleton { ProjectPaths(commandLineOptions().configurationFileName) }
}

private val dockerModule = Kodein.Module("docker") {
    import(dockerApiModule)
    import(dockerClientModule)

    bind<ContainerIOStreamer>() with singleton { ContainerIOStreamer() }
    bind<ContainerTTYManager>() with singletonWithLogger { logger -> ContainerTTYManager(instance(), instance(), logger) }
    bind<ContainerWaiter>() with singleton { ContainerWaiter(instance()) }
    bind<DockerContainerCreationRequestFactory>() with scoped(TaskScope).singleton { DockerContainerCreationRequestFactory(instance(), instance()) }
    bind<DockerContainerEnvironmentVariableProvider>() with scoped(TaskScope).singleton { DockerContainerEnvironmentVariableProvider(instance(), instance(), instance()) }
    bind<DockerContainerNameGenerator>() with scoped(TaskScope).singleton { DockerContainerNameGenerator() }
    bind<DockerfileParser>() with singleton { DockerfileParser() }
    bind<DockerIgnoreParser>() with singleton { DockerIgnoreParser() }
    bind<DockerImageBuildContextFactory>() with singleton { DockerImageBuildContextFactory(instance()) }
    bind<DockerHostNameResolver>() with singleton { DockerHostNameResolver(instance(), instance()) }
    bind<DockerHttpConfig>() with singleton { DockerHttpConfig(instance(), commandLineOptions().dockerHost, instance(), instance()) }
    bind<DockerHttpConfigDefaults>() with singleton { DockerHttpConfigDefaults(instance()) }
    bind<DockerRegistryCredentialsConfigurationFile>() with singletonWithLogger { logger -> DockerRegistryCredentialsConfigurationFile(instance(), instance(), logger) }
    bind<DockerRegistryCredentialsProvider>() with singleton { DockerRegistryCredentialsProvider(instance(), instance(), instance()) }
    bind<DockerRegistryDomainResolver>() with singleton { DockerRegistryDomainResolver() }
    bind<DockerRegistryIndexResolver>() with singleton { DockerRegistryIndexResolver() }
    bind<DockerTLSConfig>() with singleton {
        val options = commandLineOptions()

        if (options.dockerUseTLS) {
            DockerTLSConfig.EnableTLS(options.dockerVerifyTLS, options.dockerTlsCACertificatePath, options.dockerTLSCertificatePath, options.dockerTLSKeyPath)
        } else {
            DockerTLSConfig.DisableTLS
        }
    }
}

private val dockerApiModule = Kodein.Module("docker.api") {
    bind<ContainersAPI>() with singletonWithLogger { logger -> ContainersAPI(instance(), instance(), logger) }
    bind<ExecAPI>() with singletonWithLogger { logger -> ExecAPI(instance(), instance(), logger) }
    bind<ImagesAPI>() with singletonWithLogger { logger -> ImagesAPI(instance(), instance(), logger) }
    bind<NetworksAPI>() with singletonWithLogger { logger -> NetworksAPI(instance(), instance(), logger) }
    bind<SystemInfoAPI>() with singletonWithLogger { logger -> SystemInfoAPI(instance(), instance(), logger) }
    bind<VolumesAPI>() with singletonWithLogger { logger -> VolumesAPI(instance(), instance(), logger) }
}

private val dockerClientModule = Kodein.Module("docker.client") {
    bind<DockerContainersClient>() with singletonWithLogger { logger -> DockerContainersClient(instance(), instance(), instance(), instance(), instance(), logger) }
    bind<DockerExecClient>() with singletonWithLogger { logger -> DockerExecClient(instance(), instance(), logger) }
    bind<DockerImagesClient>() with singletonWithLogger { logger -> DockerImagesClient(instance(), instance(), instance(), instance(), logger) }
    bind<DockerNetworksClient>() with singleton { DockerNetworksClient(instance()) }
    bind<DockerSystemInfoClient>() with singletonWithLogger { logger -> DockerSystemInfoClient(instance(), logger) }
    bind<DockerVolumesClient>() with singleton { DockerVolumesClient(instance()) }
    bind<DockerClient>() with singleton { DockerClient(instance(), instance(), instance(), instance(), instance(), instance()) }
}

private val gitModule = Kodein.Module("git") {
    bind<GitClient>() with singleton { GitClient(instance()) }
}

private val ioModule = Kodein.Module("io") {
    bind<ApplicationPaths>() with singleton { ApplicationPaths(instance<SystemInfo>()) }
}

private val iocModule = Kodein.Module("ioc") {
    bind<DockerConfigurationKodeinFactory>() with singleton { DockerConfigurationKodeinFactory(dkodein) }
    bind<TaskKodeinFactory>() with singleton { TaskKodeinFactory(dkodein) }
    bind<SessionKodeinFactory>() with singleton { SessionKodeinFactory(dkodein, instance(), instance()) }
}

private val executionModule = Kodein.Module("execution") {
    import(runnersModule)

    bind<CacheManager>() with singleton { CacheManager(instance(), instance(), instance()) }
    bind<CancellationContext>() with scoped(TaskScope).singleton { CancellationContext() }
    bind<CleanupStagePlanner>() with scoped(TaskScope).singletonWithLogger { logger -> CleanupStagePlanner(instance(), instance(), logger) }
    bind<ConfigVariablesProvider>() with singleton { ConfigVariablesProvider(commandLineOptions().configVariableOverrides, commandLineOptions().configVariablesSourceFile, instance()) }
    bind<ContainerCommandResolver>() with singleton { ContainerCommandResolver(instance(RunOptionsType.Task)) }
    bind<ContainerDependencyGraph>() with scoped(TaskScope).singleton { instance<ContainerDependencyGraphProvider>().createGraph(instance(), context) }
    bind<ContainerDependencyGraphProvider>() with singletonWithLogger { logger -> ContainerDependencyGraphProvider(instance(), instance(), logger) }
    bind<ContainerEntrypointResolver>() with singleton { ContainerEntrypointResolver() }
    bind<InterruptionTrap>() with singleton { InterruptionTrap(instance()) }
    bind<ParallelExecutionManager>() with scoped(TaskScope).singletonWithLogger { logger -> ParallelExecutionManager(instance(), instance(), instance(), logger) }
    bind<RunAsCurrentUserConfigurationProvider>() with singleton { RunAsCurrentUserConfigurationProvider(instance(), instance(), instance(), instance()) }
    bind<RunOptions>(RunOptionsType.Overall) with singleton { RunOptions(commandLineOptions()) }
    bind<RunStagePlanner>() with scoped(TaskScope).singletonWithLogger { logger -> RunStagePlanner(instance(), logger) }
    bind<TaskRunner>() with singletonWithLogger { logger -> TaskRunner(instance(), instance(), instance(StreamType.Output), logger) }
    bind<TaskExecutionOrderResolver>() with singletonWithLogger { logger -> TaskExecutionOrderResolver(instance(), logger) }
    bind<TaskStateMachine>() with scoped(TaskScope).singletonWithLogger { logger -> TaskStateMachine(instance(), instance(RunOptionsType.Task), instance(), instance(), instance(), instance(), logger) }
    bind<TaskStepRunner>() with scoped(TaskScope).singleton { TaskStepRunner(dkodein) }
    bind<TaskSuggester>() with singleton { TaskSuggester() }
    bind<VolumeMountResolver>() with scoped(TaskScope).singleton { VolumeMountResolver(instance(), instance(), instance(), instance()) }
}

private val runnersModule = Kodein.Module("execution.model.steps.runners") {
    bind<BuildImageStepRunner>() with scoped(TaskScope).singleton { BuildImageStepRunner(instance(), instance(), instance(), instance(), instance(), instance(), instance(), instance(RunOptionsType.Task), instance()) }
    bind<CreateContainerStepRunner>() with scoped(TaskScope).singleton { CreateContainerStepRunner(instance(), instance(), instance(), instance(), instance(RunOptionsType.Task), instance()) }
    bind<PrepareTaskNetworkStepRunner>() with scoped(TaskScope).singleton { PrepareTaskNetworkStepRunner(instance(), instance(), instance()) }
    bind<DeleteTaskNetworkStepRunner>() with scoped(TaskScope).singleton { DeleteTaskNetworkStepRunner(instance()) }
    bind<DeleteTemporaryDirectoryStepRunner>() with scoped(TaskScope).singleton { DeleteTemporaryDirectoryStepRunner() }
    bind<DeleteTemporaryFileStepRunner>() with scoped(TaskScope).singleton { DeleteTemporaryFileStepRunner() }
    bind<InitialiseCachesStepRunner>() with scoped(TaskScope).singleton { InitialiseCachesStepRunner(instance(), commandLineOptions().linuxCacheInitImageName, instance(), instance(), instance(), instance(), instance(), instance(), instance()) }
    bind<PullImageStepRunner>() with scoped(TaskScope).singleton { PullImageStepRunner(instance(), instance()) }
    bind<RemoveContainerStepRunner>() with scoped(TaskScope).singleton { RemoveContainerStepRunner(instance()) }
    bind<RunContainerSetupCommandsStepRunner>() with scoped(TaskScope).singleton { RunContainerSetupCommandsStepRunner(instance(), instance(), instance(), instance(RunOptionsType.Task), instance(), instance()) }
    bind<RunContainerStepRunner>() with scoped(TaskScope).singleton { RunContainerStepRunner(instance(), instance(), instance()) }
    bind<StopContainerStepRunner>() with scoped(TaskScope).singleton { StopContainerStepRunner(instance()) }
    bind<WaitForContainerToBecomeHealthyStepRunner>() with scoped(TaskScope).singleton { WaitForContainerToBecomeHealthyStepRunner(instance(), instance(), instance()) }
}

private val loggingModule = Kodein.Module("logging") {
    bind<ApplicationInfoLogger>() with singletonWithLogger { logger -> ApplicationInfoLogger(logger, instance(), instance(), instance(), instance(), instance(), instance()) }
    bind<HttpLoggingInterceptor>() with singletonWithLogger { logger -> HttpLoggingInterceptor(logger) }
    bind<LoggerFactory>() with singleton { LoggerFactory(instance()) }
    bind<LogMessageWriter>() with singleton { LogMessageWriter(Json.forLogging) }
    bind<StandardAdditionalDataSource>() with singleton { StandardAdditionalDataSource(instance()) }
}

private val osModule = Kodein.Module("os") {
    bind<ConsoleDimensions>() with singletonWithLogger { logger -> ConsoleDimensions(instance(), instance(), logger) }
    bind<ConsoleInfo>() with singletonWithLogger { logger -> ConsoleInfo(instance(), instance(), instance(), logger) }
    bind<HostEnvironmentVariables>() with singleton { HostEnvironmentVariables.current }
    bind<ProcessRunner>() with singletonWithLogger { logger -> ProcessRunner(logger) }
    bind<SignalListener>() with singleton { SignalListener(instance()) }
    bind<SystemInfo>() with singleton { SystemInfo(instance(), instance()) }
}

private val proxiesModule = Kodein.Module("proxies") {
    bind<ProxyEnvironmentVariablePreprocessor>() with singletonWithLogger { logger -> ProxyEnvironmentVariablePreprocessor(instance(), logger) }
    bind<ProxyEnvironmentVariablesProvider>() with singleton { ProxyEnvironmentVariablesProvider(instance(), instance()) }
}

private val unixModule = Kodein.Module("os.unix") {
    bind<ApplicationResolver>() with singleton { ApplicationResolver(instance()) }
    bind<ConsoleManager>() with singletonWithLogger { logger -> UnixConsoleManager(instance(), instance(), instance(), logger) }
    bind<NativeMethods>() with singleton { UnixNativeMethods(instance()) }
}

private val windowsModule = Kodein.Module("os.windows") {
    bind<ConsoleManager>() with singletonWithLogger { logger -> WindowsConsoleManager(instance(), instance(), logger) }
    bind<WindowsNativeMethods>() with singleton { WindowsNativeMethods(instance()) }
    bind<NativeMethods>() with singleton { instance<WindowsNativeMethods>() }
}

private val uiModule = Kodein.Module("ui") {
    bind<EventLoggerProvider>() with singleton {
        EventLoggerProvider(
            instance(),
            instance(StreamType.Output),
            instance(StreamType.Error),
            instance(StreamType.Output),
            instance(StreamType.Input),
            instance(),
            instance(),
            instance(),
            commandLineOptions().requestedOutputStyle,
            commandLineOptions().disableColorOutput
        )
    }

    bind<Console>(StreamType.Output) with singleton { Console(instance(StreamType.Output), enableComplexOutput = !commandLineOptions().disableColorOutput && nativeMethods().determineIfStdoutIsTTY(), consoleDimensions = instance()) }
    bind<Console>(StreamType.Error) with singleton { Console(instance(StreamType.Error), enableComplexOutput = !commandLineOptions().disableColorOutput && nativeMethods().determineIfStderrIsTTY(), consoleDimensions = instance()) }
    bind<ContainerIOStreamingOptions>() with scoped(TaskScope).singleton { instance<EventLogger>().ioStreamingOptions }
    bind<EventLogger>() with scoped(TaskScope).singleton { instance<EventLoggerProvider>().getEventLogger(context, instance()) }
    bind<FailureErrorMessageFormatter>() with scoped(TaskScope).singleton { FailureErrorMessageFormatter(instance(RunOptionsType.Task), instance()) }
    bind<StartupProgressDisplayProvider>() with singleton { StartupProgressDisplayProvider(instance()) }
}

private val updatesModule = Kodein.Module("updates") {
    bind<UpdateInfoDownloader>() with singletonWithLogger { logger -> UpdateInfoDownloader(instance(), logger) }
    bind<UpdateInfoStorage>() with singletonWithLogger { logger -> UpdateInfoStorage(instance(), logger) }
    bind<UpdateInfoUpdater>() with singletonWithLogger { logger -> UpdateInfoUpdater(instance(), instance(), logger) }
    bind<UpdateNotifier>() with singletonWithLogger { logger -> UpdateNotifier(commandLineOptions().disableUpdateNotification, instance(), instance(), instance(), instance(StreamType.Output), logger) }
}

private val wrapperModule = Kodein.Module("wrapper") {
    bind<WrapperCache>() with singletonWithLogger { logger -> WrapperCache(instance(), instance(), logger) }
    bind<WrapperCacheCleanupTask>() with singletonWithLogger { logger -> WrapperCacheCleanupTask(!commandLineOptions().disableWrapperCacheCleanup, instance(), instance(), logger) }
}

private val coreModule = Kodein.Module("core") {
    bind<VersionInfo>() with singleton { VersionInfo() }
}

private fun DKodein.commandLineOptions(): CommandLineOptions = this.instance()
private fun DKodein.nativeMethods(): NativeMethods = this.instance()
