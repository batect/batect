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

import batect.config.Configuration
import batect.config.ExpressionEvaluationContext
import batect.docker.client.DockerContainerType
import batect.execution.ConfigVariablesProvider
import batect.os.HostEnvironmentVariables
import org.kodein.di.Copy
import org.kodein.di.DKodein
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance

class SessionKodeinFactory(
    private val baseKodein: DKodein,
    private val hostEnvironmentVariables: HostEnvironmentVariables,
    private val configVariablesProvider: ConfigVariablesProvider
) {
    fun create(config: Configuration, containerType: DockerContainerType): DKodein = Kodein.direct {
        extend(baseKodein, copy = Copy.All)
        bind<Configuration>() with instance(config)
        bind<DockerContainerType>() with instance(containerType)
        bind<ExpressionEvaluationContext>() with instance(ExpressionEvaluationContext(hostEnvironmentVariables, configVariablesProvider.build(config)))
    }
}
