package decompose

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.throws
import decompose.config.Configuration
import decompose.config.Container
import decompose.config.ContainerMap
import decompose.config.Task
import decompose.config.TaskMap
import decompose.config.TaskRunConfiguration
import decompose.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DependencyResolverSpec : Spek({
    describe("a dependency resolver") {
        val resolver = DependencyResolver()
        val runConfig = TaskRunConfiguration("some-container", "some-command")

        on("creating a dependency manager for a task with no dependencies") {
            val task = Task("the-task", runConfig, emptySet())
            val config = Configuration("the-project", TaskMap(task), ContainerMap())
            val dependencies = resolver.resolveDependencies(config, task)

            it("resolves an empty list of dependencies") {
                assert.that(dependencies, isEmpty)
            }
        }

        on("creating a dependency manager for a task with a dependency") {
            val dependency = Container("the-dependency", "/build_dir")
            val task = Task("the-task", runConfig, setOf(dependency.name))
            val config = Configuration("the-project", TaskMap(task), ContainerMap(dependency))
            val dependencies = resolver.resolveDependencies(config, task)

            it("resolves a list of dependencies with just that dependency") {
                assert.that(dependencies, equalTo(setOf(dependency)))
            }
        }

        on("creating a dependency manager for a task with many dependencies") {
            val dependency1 = Container("dependency1", "/build_dir")
            val dependency2 = Container("dependency2", "/build_dir")
            val dependency3 = Container("dependency3", "/build_dir")
            val otherContainer = Container("something-else", "/build_dir")
            val task = Task("the-task", runConfig, setOf(dependency1.name, dependency2.name, dependency3.name))
            val config = Configuration("the-project", TaskMap(task), ContainerMap(dependency1, dependency2, dependency3, otherContainer))
            val dependencies = resolver.resolveDependencies(config, task)

            it("resolves a list of dependencies with those dependencies") {
                assert.that(dependencies, equalTo(setOf(dependency1, dependency2, dependency3)))
            }
        }

        on("creating a dependency manager for a task with a dependency that does not exist") {
            val otherContainer = Container("something-else", "/build_dir")
            val task = Task("the-task", runConfig, setOf("dependency"))
            val config = Configuration("the-project", TaskMap(task), ContainerMap(otherContainer))

            it("raises an appropriate exception") {
                assert.that({ resolver.resolveDependencies(config, task) }, throws<DependencyResolutionFailedException>(withMessage("The container 'dependency' referenced by task 'the-task' does not exist.")))
            }
        }

        on("creating a dependency manager for a task with a dependency on the task container") {
            val task = Task("the-task", runConfig, setOf(runConfig.container))
            val config = Configuration("the-project", TaskMap(task), ContainerMap())

            it("raises an appropriate exception") {
                assert.that({ resolver.resolveDependencies(config, task) }, throws<DependencyResolutionFailedException>(withMessage("The task 'the-task' cannot depend on the container 'some-container' and also run it.")))
            }
        }
    }
})
