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

import batect.config.ExpressionEvaluationContext
import batect.config.Task
import batect.config.TaskSpecialisedConfiguration
import batect.config.TaskSpecialisedConfigurationFactory
import batect.execution.ConfigVariablesProvider
import batect.execution.RunOptions
import batect.os.HostEnvironmentVariables
import org.kodein.di.DirectDI
import org.kodein.di.bind
import org.kodein.di.direct
import org.kodein.di.on
import org.kodein.di.scoped
import org.kodein.di.singleton
import org.kodein.di.subDI

class TaskKodeinFactory(
    private val baseKodein: DirectDI,
    private val hostEnvironmentVariables: HostEnvironmentVariables,
    private val configVariablesProvider: ConfigVariablesProvider,
    private val taskSpecialisedConfigurationFactory: TaskSpecialisedConfigurationFactory
) {
    fun create(task: Task, runOptions: RunOptions): TaskKodein {
        val taskSpecialisedConfiguration = taskSpecialisedConfigurationFactory.create(task)
        val expressionEvaluationContext = ExpressionEvaluationContext(hostEnvironmentVariables, configVariablesProvider.build(taskSpecialisedConfiguration))

        return TaskKodein(
            task,
            subDI(baseKodein.di) {
                bind<RunOptions>() with scoped(TaskScope).singleton { runOptions }
                bind<TaskSpecialisedConfiguration>() with scoped(TaskScope).singleton { taskSpecialisedConfiguration }
                bind<ExpressionEvaluationContext>() with scoped(TaskScope).singleton { expressionEvaluationContext }

                import(taskScopeModule)
            }.direct.on(task)
        )
    }
}

class TaskKodein(private val task: Task, kodein: DirectDI) : DirectDI by kodein, AutoCloseable {
    override fun close() = TaskScope.close(task)
}
