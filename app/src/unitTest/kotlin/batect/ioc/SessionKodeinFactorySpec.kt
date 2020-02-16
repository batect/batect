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
import batect.testutils.createForEachTest
import batect.testutils.on
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SessionKodeinFactorySpec : Spek({
    describe("a session Kodein factory") {
        val baseKodein = Kodein.direct {
            bind<String>("some string") with instance("The string value")
        }

        val configVariables = mapOf("SOME_VAR" to "some value")
        val configVariablesProvider by createForEachTest {
            mock<ConfigVariablesProvider> {
                on { build(any()) } doReturn configVariables
            }
        }

        val hostEnvironmentVariables = HostEnvironmentVariables()

        val factory by createForEachTest { SessionKodeinFactory(baseKodein, hostEnvironmentVariables, configVariablesProvider) }

        on("creating a task Kodein context") {
            val config by createForEachTest { mock<Configuration>() }
            val containerType by createForEachTest { mock<DockerContainerType>() }
            val extendedKodein by runForEachTest { factory.create(config, containerType) }

            it("includes the configuration from the original instance") {
                assertThat(extendedKodein.instance<String>("some string"), equalTo("The string value"))
            }

            it("includes the configuration") {
                assertThat(extendedKodein.instance<Configuration>(), equalTo(config))
            }

            it("includes the container type") {
                assertThat(extendedKodein.instance<DockerContainerType>(), equalTo(containerType))
            }

            it("builds the set of config variables") {
                verify(configVariablesProvider).build(config)
            }

            it("includes the expression evaluation context") {
                assertThat(extendedKodein.instance<ExpressionEvaluationContext>(), equalTo(ExpressionEvaluationContext(hostEnvironmentVariables, configVariables)))
            }
        }
    }
})
