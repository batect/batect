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

import batect.config.Task
import batect.execution.RunOptions
import batect.testutils.createForEachTest
import batect.testutils.on
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockitokotlin2.mock
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.scoped
import org.kodein.di.generic.singleton
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TaskKodeinFactorySpec : Spek({
    describe("a task Kodein factory") {
        val baseKodein = Kodein.direct {
            bind<String>("some string") with instance("The string value")
            bind<TaskReference>() with scoped(TaskScope).singleton { TaskReference(context) }
        }

        val factory by createForEachTest { TaskKodeinFactory(baseKodein) }

        on("creating a task Kodein context") {
            val task by createForEachTest { mock<Task>() }
            val runOptions by createForEachTest { mock<RunOptions>() }
            val extendedKodein by runForEachTest { factory.create(task, runOptions) }

            it("includes the configuration from the original instance") {
                assertThat(extendedKodein.instance<String>("some string"), equalTo("The string value"))
            }

            it("includes the run options") {
                assertThat(extendedKodein.instance<RunOptions>(RunOptionsType.Task), equalTo(runOptions))
            }

            it("sets the context correctly") {
                assertThat(extendedKodein.instance<TaskReference>(), equalTo(TaskReference(task)))
            }
        }
    }
})

private data class TaskReference(val task: Task)
