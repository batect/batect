/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.ioc

import batect.config.TaskSpecialisedConfigurationFactory
import batect.execution.ImageTaggingValidator
import batect.execution.SessionRunner
import batect.execution.TaskExecutionOrderResolver
import batect.execution.TaskRunner
import batect.logging.singletonWithLogger
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

val sessionScopeModule = DI.Module("Session scope: root") {
    bind<ImageTaggingValidator>() with singleton { ImageTaggingValidator(instance()) }
    bind<SessionRunner>() with singleton { SessionRunner(instance(), instance(), instance(), instance(StreamType.Output), instance(), instance()) }
    bind<TaskExecutionOrderResolver>() with singletonWithLogger { logger -> TaskExecutionOrderResolver(instance(), instance(), instance(), logger) }
    bind<TaskKodeinFactory>() with singleton { TaskKodeinFactory(directDI, instance(), instance(), instance()) }
    bind<TaskRunner>() with singletonWithLogger { logger -> TaskRunner(instance(), instance(), instance(StreamType.Output), instance(), logger) }
    bind<TaskSpecialisedConfigurationFactory>() with singletonWithLogger { logger -> TaskSpecialisedConfigurationFactory(instance(), instance(), logger) }
}
