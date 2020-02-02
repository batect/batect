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
import batect.config.Task
import batect.docker.client.DockerContainerType
import batect.execution.RunOptions
import org.kodein.di.Copy
import org.kodein.di.DKodein
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.on
import org.kodein.di.generic.scoped
import org.kodein.di.generic.singleton

class TaskKodeinFactory(
    private val baseKodein: DKodein
) {
    fun create(config: Configuration, task: Task, runOptions: RunOptions, containerType: DockerContainerType): TaskKodein = TaskKodein(task, Kodein.direct {
        extend(baseKodein, copy = Copy.All)
        bind<RunOptions>(RunOptionsType.Task) with scoped(TaskScope).singleton { runOptions }

        // TODO: move these up to a higher level (they're not task-specific)
        bind<Configuration>() with instance(config)
        bind<DockerContainerType>() with instance(containerType)
    }.on(task))
}

class TaskKodein(private val task: Task, kodein: DKodein) : DKodein by kodein, AutoCloseable {
    override fun close() = TaskScope.close(task)
}
