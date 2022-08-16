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

import batect.execution.CacheManager
import batect.execution.RunAsCurrentUserConfigurationProvider
import batect.telemetry.DockerTelemetryCollector
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

val dockerConfigurationModule = DI.Module("Docker configuration scope: root") {
    bind<CacheManager>() with singleton { CacheManager(instance(), instance(), instance()) }
    bind<DockerTelemetryCollector>() with singleton { DockerTelemetryCollector(instance(), instance()) }
    bind<RunAsCurrentUserConfigurationProvider>() with singleton { RunAsCurrentUserConfigurationProvider(instance(), instance(), instance(), instance(), instance()) }
    bind<SessionKodeinFactory>() with singleton { SessionKodeinFactory(directDI) }
}
