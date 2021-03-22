/*
   Copyright 2017-2021 Charles Korn.

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

import batect.docker.DockerContainerCreationRequestFactory
import batect.docker.DockerContainerEnvironmentVariableProvider
import batect.docker.DockerResourceNameGenerator
import batect.execution.ContainerDependencyGraph
import batect.execution.ContainerDependencyGraphProvider
import batect.execution.ParallelExecutionManager
import batect.execution.TaskStateMachine
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
import batect.logging.singletonWithLogger
import batect.primitives.CancellationContext
import batect.ui.EventLogger
import batect.ui.EventLoggerProvider
import batect.ui.FailureErrorMessageFormatter
import batect.ui.containerio.ContainerIOStreamingOptions
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.scoped
import org.kodein.di.singleton

val taskScopeModule = DI.Module("Task scope: root") {
    import(dockerModule)
    import(executionModule)
    import(uiModule)
}

private val dockerModule = DI.Module("Task scope: docker") {
    bind<DockerContainerCreationRequestFactory>() with scoped(TaskScope).singleton { DockerContainerCreationRequestFactory(instance(), instance(), instance()) }
    bind<DockerContainerEnvironmentVariableProvider>() with scoped(TaskScope).singleton { DockerContainerEnvironmentVariableProvider(instance(), instance(), instance(), instance()) }
    bind<DockerResourceNameGenerator>() with scoped(TaskScope).singleton { DockerResourceNameGenerator(instance()) }
}

private val executionModule = DI.Module("Task scope: execution") {
    import(runnersModule)

    bind<CancellationContext>() with scoped(TaskScope).singleton { CancellationContext() }
    bind<CleanupStagePlanner>() with scoped(TaskScope).singletonWithLogger { logger -> CleanupStagePlanner(instance(), instance(), logger) }
    bind<ContainerDependencyGraph>() with scoped(TaskScope).singleton { instance<ContainerDependencyGraphProvider>().createGraph(instance(), context) }
    bind<ContainerDependencyGraphProvider>() with scoped(TaskScope).singletonWithLogger { logger -> ContainerDependencyGraphProvider(logger) }
    bind<ParallelExecutionManager>() with scoped(TaskScope).singletonWithLogger { logger -> ParallelExecutionManager(instance(), instance(), instance(), instance(), commandLineOptions().maximumLevelOfParallelism, logger) }
    bind<RunStagePlanner>() with scoped(TaskScope).singletonWithLogger { logger -> RunStagePlanner(instance(), logger) }
    bind<TaskStateMachine>() with scoped(TaskScope).singletonWithLogger { logger -> TaskStateMachine(instance(), instance(), instance(), instance(), instance(), instance(), logger) }
    bind<TaskStepRunner>() with scoped(TaskScope).singleton { TaskStepRunner(directDI) }
    bind<VolumeMountResolver>() with scoped(TaskScope).singleton { VolumeMountResolver(instance(), instance(), instance(), instance()) }
}

private val runnersModule = DI.Module("Task scope: execution.model.steps.runners") {
    bind<BuildImageStepRunner>() with scoped(TaskScope).singleton { BuildImageStepRunner(instance(), instance(), instance(), instance(), instance(), instance(), instance(), instance(), instance(), instance(), instance()) }
    bind<CreateContainerStepRunner>() with scoped(TaskScope).singleton { CreateContainerStepRunner(instance(), instance(), instance(), instance(), instance()) }
    bind<PrepareTaskNetworkStepRunner>() with scoped(TaskScope).singleton { PrepareTaskNetworkStepRunner(instance(), instance(), instance(), instance()) }
    bind<DeleteTaskNetworkStepRunner>() with scoped(TaskScope).singleton { DeleteTaskNetworkStepRunner(instance()) }
    bind<DeleteTemporaryDirectoryStepRunner>() with scoped(TaskScope).singleton { DeleteTemporaryDirectoryStepRunner() }
    bind<DeleteTemporaryFileStepRunner>() with scoped(TaskScope).singleton { DeleteTemporaryFileStepRunner() }
    bind<InitialiseCachesStepRunner>() with scoped(TaskScope).singleton { InitialiseCachesStepRunner(instance(), commandLineOptions().linuxCacheInitImageName, instance(), instance(), instance(), instance(), instance(), instance(), instance()) }
    bind<PullImageStepRunner>() with scoped(TaskScope).singleton { PullImageStepRunner(instance(), instance()) }
    bind<RemoveContainerStepRunner>() with scoped(TaskScope).singleton { RemoveContainerStepRunner(instance()) }
    bind<RunContainerSetupCommandsStepRunner>() with scoped(TaskScope).singleton { RunContainerSetupCommandsStepRunner(instance(), instance(), instance(), instance(), instance()) }
    bind<RunContainerStepRunner>() with scoped(TaskScope).singleton { RunContainerStepRunner(instance(), instance(), instance()) }
    bind<StopContainerStepRunner>() with scoped(TaskScope).singleton { StopContainerStepRunner(instance()) }
    bind<WaitForContainerToBecomeHealthyStepRunner>() with scoped(TaskScope).singleton { WaitForContainerToBecomeHealthyStepRunner(instance(), instance(), instance()) }
}

private val uiModule = DI.Module("Task scope: ui") {
    bind<ContainerIOStreamingOptions>() with scoped(TaskScope).singleton { instance<EventLogger>().ioStreamingOptions }
    bind<EventLogger>() with scoped(TaskScope).singleton { instance<EventLoggerProvider>().getEventLogger(context, instance()) }
    bind<FailureErrorMessageFormatter>() with scoped(TaskScope).singleton { FailureErrorMessageFormatter(instance(), instance()) }

    bind<EventLoggerProvider>() with scoped(TaskScope).singleton {
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
}
