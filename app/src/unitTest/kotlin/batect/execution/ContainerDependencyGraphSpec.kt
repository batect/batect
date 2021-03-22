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

package batect.execution

import batect.config.Container
import batect.config.ContainerMap
import batect.config.LiteralValue
import batect.config.PortMapping
import batect.config.Task
import batect.config.TaskContainerCustomisation
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.config.TaskSpecialisedConfiguration
import batect.os.Command
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ContainerDependencyGraphSpec : Spek({
    describe("a container dependency graph") {
        given("a task with only prerequisites") {
            val task = Task("the-task", runConfiguration = null)
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task))

            on("creating the dependency graph") {
                it("throws an appropriate exception") {
                    assertThat({ ContainerDependencyGraph(config, task) }, throws(withMessage("Cannot create a container dependency graph for a task that only has prerequisites.")))
                }
            }
        }

        given("a task with no dependencies") {
            given("the task does not override the container's working directory") {
                val container = Container("some-container", imageSourceDoesNotMatter(), command = Command.parse("some-container-command"), entrypoint = Command.parse("some-task-entrypoint"), workingDirectory = "task-working-dir-that-wont-be-used")
                val runConfig = TaskRunConfiguration(container.name, Command.parse("some-command"), Command.parse("some-entrypoint"), mapOf("SOME_EXTRA_VALUE" to LiteralValue("the value")), setOf(PortMapping(123, 456)), "some-task-specific-working-dir")
                val task = Task("the-task", runConfig, dependsOnContainers = emptySet())
                val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(container))
                val graph = ContainerDependencyGraph(config, task)

                on("getting the task container node") {
                    val node = graph.taskContainerNode

                    it("depends on nothing") {
                        assertThat(node.dependsOn, isEmpty)
                    }

                    it("is depended on by nothing") {
                        assertThat(node.dependedOnBy, isEmpty)
                    }

                    it("indicates that it is the root node of the graph") {
                        assertThat(node.isRootNode, equalTo(true))
                    }

                    it("is the same node as the node for the task container") {
                        assertThat(graph.nodeFor(container), equalTo(node))
                    }
                }

                on("getting the node for a container not part of the graph") {
                    val otherContainer = Container("the-other-container", imageSourceDoesNotMatter())

                    it("throws an exception") {
                        assertThat({ graph.nodeFor(otherContainer) }, throws<IllegalArgumentException>(withMessage("Container 'the-other-container' is not part of this dependency graph.")))
                    }
                }
            }

            given("the task contains customisations for a container") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val runConfig = TaskRunConfiguration(container.name)
                val customisations = mapOf("some-other-container" to TaskContainerCustomisation(workingDirectory = "/work"))
                val task = Task("the-task", runConfig, customisations = customisations)
                val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(container))

                on("creating the graph") {
                    it("throws an exception") {
                        assertThat({ ContainerDependencyGraph(config, task) }, throws<CustomisationForContainerNotInTaskDependencyGraphException>(withMessage("The task 'the-task' has customisations for container 'some-other-container', but the container 'some-other-container' will not be started as part of the task.")))
                    }
                }
            }
        }

        given("a task that refers to a container that does not exist") {
            val runConfig = TaskRunConfiguration("some-non-existent-container", Command.parse("some-command"))
            val task = Task("the-task", runConfig, dependsOnContainers = emptySet())
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap())

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ ContainerDependencyGraph(config, task) }, throws<ContainerDoesNotExistException>(withMessage("The container 'some-non-existent-container' referenced by task 'the-task' does not exist.")))
                }
            }
        }

        given("a task with a dependency") {
            val taskContainer = Container("some-container", imageSourceDoesNotMatter(), command = Command.parse("task-command-that-wont-be-used"), entrypoint = Command.parse("sh"), workingDirectory = "task-working-dir-that-wont-be-used")
            val dependencyContainer = Container("dependency-container", imageSourceDoesNotMatter(), command = Command.parse("dependency-command"), workingDirectory = "dependency-working-dir")
            val runConfig = TaskRunConfiguration(taskContainer.name, Command.parse("some-command"), Command.parse("some-entrypoint"), mapOf("SOME_EXTRA_VALUE" to LiteralValue("the value")), setOf(PortMapping(123, 456)), "some-task-specific-working-dir")
            val task = Task("the-task", runConfig, dependsOnContainers = setOf(dependencyContainer.name))
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer))
            val graph = ContainerDependencyGraph(config, task)

            on("getting the task container node") {
                val node = graph.taskContainerNode

                it("depends on the dependency container") {
                    assertThat(node.dependsOn.mapToSet { it.container }, equalTo(setOf(dependencyContainer)))
                }

                it("is depended on by nothing") {
                    assertThat(node.dependedOnBy, isEmpty)
                }

                it("indicates that it is the root node of the graph") {
                    assertThat(node.isRootNode, equalTo(true))
                }

                it("is the same node as the node for the task container") {
                    assertThat(graph.nodeFor(taskContainer), equalTo(node))
                }
            }

            on("getting the dependency container node") {
                val node = graph.nodeFor(dependencyContainer)

                it("depends on nothing") {
                    assertThat(node.dependsOn, isEmpty)
                }

                it("is depended on by the task container") {
                    assertThat(node.dependedOnBy.mapToSet { it.container }, equalTo(setOf(taskContainer)))
                }

                it("indicates that it is not the root node of the graph") {
                    assertThat(node.isRootNode, equalTo(false))
                }
            }
        }

        given("a task with many dependencies") {
            val taskContainer = Container("some-container", imageSourceDoesNotMatter())
            val dependencyContainer1 = Container("dependency-container-1", imageSourceDoesNotMatter())
            val dependencyContainer2 = Container("dependency-container-2", imageSourceDoesNotMatter())
            val dependencyContainer3 = Container("dependency-container-3", imageSourceDoesNotMatter())
            val runConfig = TaskRunConfiguration(taskContainer.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig, dependsOnContainers = setOf(dependencyContainer1.name, dependencyContainer2.name, dependencyContainer3.name))
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer1, dependencyContainer2, dependencyContainer3))
            val graph = ContainerDependencyGraph(config, task)

            on("getting the task container node") {
                val node = graph.taskContainerNode

                it("depends on the dependency containers") {
                    assertThat(node.dependsOn.mapToSet { it.container }, equalTo(setOf(dependencyContainer1, dependencyContainer2, dependencyContainer3)))
                }

                it("is depended on by nothing") {
                    assertThat(node.dependedOnBy, isEmpty)
                }

                it("indicates that it is the root node of the graph") {
                    assertThat(node.isRootNode, equalTo(true))
                }

                it("is the same node as the node for the task container") {
                    assertThat(graph.nodeFor(taskContainer), equalTo(node))
                }
            }

            listOf(dependencyContainer1, dependencyContainer2, dependencyContainer3).forEach { container ->
                on("getting the node for dependency container '${container.name}'") {
                    val node = graph.nodeFor(container)

                    it("depends on nothing") {
                        assertThat(node.dependsOn, isEmpty)
                    }

                    it("is depended on by the task container") {
                        assertThat(node.dependedOnBy.mapToSet { it.container }, equalTo(setOf(taskContainer)))
                    }

                    it("indicates that it is not the root node of the graph") {
                        assertThat(node.isRootNode, equalTo(false))
                    }
                }
            }
        }

        given("a task with a dependency that does not exist") {
            val taskContainer = Container("some-container", imageSourceDoesNotMatter())
            val runConfig = TaskRunConfiguration(taskContainer.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig, dependsOnContainers = setOf("non-existent-dependency"))
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ ContainerDependencyGraph(config, task) }, throws<ContainerDoesNotExistException>(withMessage("The container 'non-existent-dependency' referenced by task 'the-task' does not exist.")))
                }
            }
        }

        given("a task with a dependency on the task container") {
            val taskContainer = Container("some-container", imageSourceDoesNotMatter())
            val runConfig = TaskRunConfiguration(taskContainer.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig, dependsOnContainers = setOf(taskContainer.name))
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ ContainerDependencyGraph(config, task) }, throws<MainTaskContainerIsDependencyException>(withMessage("The task 'the-task' cannot have the container 'some-container' as both the main task container and also a dependency.")))
                }
            }
        }

        given("a task with a container that has some direct dependencies") {
            val dependencyContainer1 = Container("dependency-container-1", imageSourceDoesNotMatter())
            val dependencyContainer2 = Container("dependency-container-2", imageSourceDoesNotMatter())
            val dependencyContainer3 = Container("dependency-container-3", imageSourceDoesNotMatter())
            val taskContainer = Container("some-container", imageSourceDoesNotMatter(), dependencies = setOf(dependencyContainer1.name, dependencyContainer2.name, dependencyContainer3.name))
            val runConfig = TaskRunConfiguration(taskContainer.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig)
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer1, dependencyContainer2, dependencyContainer3))
            val graph = ContainerDependencyGraph(config, task)

            on("getting the task container node") {
                val node = graph.taskContainerNode

                it("depends on the dependency containers") {
                    assertThat(node.dependsOn.mapToSet { it.container }, equalTo(setOf(dependencyContainer1, dependencyContainer2, dependencyContainer3)))
                }

                it("is depended on by nothing") {
                    assertThat(node.dependedOnBy, isEmpty)
                }

                it("indicates that it is the root node of the graph") {
                    assertThat(node.isRootNode, equalTo(true))
                }

                it("is the same node as the node for the task container") {
                    assertThat(graph.nodeFor(taskContainer), equalTo(node))
                }
            }

            listOf(dependencyContainer1, dependencyContainer2, dependencyContainer3).forEach { container ->
                on("getting the node for dependency container '${container.name}'") {
                    val node = graph.nodeFor(container)

                    it("depends on nothing") {
                        assertThat(node.dependsOn, isEmpty)
                    }

                    it("is depended on by the task container") {
                        assertThat(node.dependedOnBy.mapToSet { it.container }, equalTo(setOf(taskContainer)))
                    }

                    it("indicates that it is not the root node of the graph") {
                        assertThat(node.isRootNode, equalTo(false))
                    }
                }
            }
        }

        given("a task with a container that has a dependency which itself has a dependency") {
            val dependencyContainer1 = Container("dependency-container-1", imageSourceDoesNotMatter())
            val dependencyContainer2 = Container("dependency-container-2", imageSourceDoesNotMatter(), dependencies = setOf(dependencyContainer1.name))
            val taskContainer = Container("some-container", imageSourceDoesNotMatter(), dependencies = setOf(dependencyContainer2.name))
            val runConfig = TaskRunConfiguration(taskContainer.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig)
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer1, dependencyContainer2))
            val graph = ContainerDependencyGraph(config, task)

            on("getting the task container node") {
                val node = graph.taskContainerNode

                it("depends on its direct dependency") {
                    assertThat(node.dependsOn.mapToSet { it.container }, equalTo(setOf(dependencyContainer2)))
                }

                it("is depended on by nothing") {
                    assertThat(node.dependedOnBy, isEmpty)
                }

                it("indicates that it is the root node of the graph") {
                    assertThat(node.isRootNode, equalTo(true))
                }

                it("is the same node as the node for the task container") {
                    assertThat(graph.nodeFor(taskContainer), equalTo(node))
                }
            }

            on("getting the node for the dependency container that is a direct dependency of the task container") {
                val node = graph.nodeFor(dependencyContainer2)

                it("depends on its direct dependency") {
                    assertThat(node.dependsOn.mapToSet { it.container }, equalTo(setOf(dependencyContainer1)))
                }

                it("is depended on by the task container") {
                    assertThat(node.dependedOnBy.mapToSet { it.container }, equalTo(setOf(taskContainer)))
                }

                it("indicates that it is not the root node of the graph") {
                    assertThat(node.isRootNode, equalTo(false))
                }
            }

            on("getting the node for the dependency container that is a indirect dependency of the task container") {
                val node = graph.nodeFor(dependencyContainer1)

                it("depends on nothing") {
                    assertThat(node.dependsOn, isEmpty)
                }

                it("is depended on by the direct dependency of the task container") {
                    assertThat(node.dependedOnBy.mapToSet { it.container }, equalTo(setOf(dependencyContainer2)))
                }

                it("indicates that it is not the root node of the graph") {
                    assertThat(node.isRootNode, equalTo(false))
                }
            }
        }

        given("a task with a container that has two direct dependencies which themselves share a single dependency") {
            val containerWithNoDependencies = Container("no-dependencies", imageSourceDoesNotMatter())
            val dependencyContainer1 = Container("dependency-container-1", imageSourceDoesNotMatter(), dependencies = setOf(containerWithNoDependencies.name))
            val dependencyContainer2 = Container("dependency-container-2", imageSourceDoesNotMatter(), dependencies = setOf(containerWithNoDependencies.name))
            val taskContainer = Container("some-container", imageSourceDoesNotMatter(), dependencies = setOf(dependencyContainer1.name, dependencyContainer2.name))
            val runConfig = TaskRunConfiguration(taskContainer.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig)
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer1, dependencyContainer2, containerWithNoDependencies))
            val graph = ContainerDependencyGraph(config, task)

            on("getting the task container node") {
                val node = graph.taskContainerNode

                it("depends on its direct dependencies") {
                    assertThat(node.dependsOn.mapToSet { it.container }, equalTo(setOf(dependencyContainer1, dependencyContainer2)))
                }

                it("is depended on by nothing") {
                    assertThat(node.dependedOnBy, isEmpty)
                }

                it("indicates that it is the root node of the graph") {
                    assertThat(node.isRootNode, equalTo(true))
                }

                it("is the same node as the node for the task container") {
                    assertThat(graph.nodeFor(taskContainer), equalTo(node))
                }
            }

            listOf(dependencyContainer1, dependencyContainer2).forEach { container ->
                on("getting the node for direct dependency container '${container.name}'") {
                    val node = graph.nodeFor(container)

                    it("depends on the common dependency") {
                        assertThat(node.dependsOn.mapToSet { it.container }, equalTo(setOf(containerWithNoDependencies)))
                    }

                    it("is depended on by the task container") {
                        assertThat(node.dependedOnBy.mapToSet { it.container }, equalTo(setOf(taskContainer)))
                    }

                    it("indicates that it is not the root node of the graph") {
                        assertThat(node.isRootNode, equalTo(false))
                    }
                }
            }

            on("getting the node for the dependency container that has no dependencies") {
                val node = graph.nodeFor(containerWithNoDependencies)

                it("depends on nothing") {
                    assertThat(node.dependsOn, isEmpty)
                }

                it("is depended on by the two direct dependencies of the task container") {
                    assertThat(node.dependedOnBy.mapToSet { it.container }, equalTo(setOf(dependencyContainer1, dependencyContainer2)))
                }

                it("indicates that it is not the root node of the graph") {
                    assertThat(node.isRootNode, equalTo(false))
                }
            }
        }

        given("a task with a container that depends on containers A and B where B also depends on A") {
            val containerA = Container("container-a", imageSourceDoesNotMatter())
            val containerB = Container("container-b", imageSourceDoesNotMatter(), dependencies = setOf(containerA.name))
            val taskContainer = Container("some-container", imageSourceDoesNotMatter(), dependencies = setOf(containerB.name, containerA.name))
            val runConfig = TaskRunConfiguration(taskContainer.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig)
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer, containerB, containerA))
            val graph = ContainerDependencyGraph(config, task)

            on("getting the task container node") {
                val node = graph.taskContainerNode

                it("depends on its direct dependencies") {
                    assertThat(node.dependsOn.mapToSet { it.container }, equalTo(setOf(containerA, containerB)))
                }

                it("is depended on by nothing") {
                    assertThat(node.dependedOnBy, isEmpty)
                }

                it("indicates that it is the root node of the graph") {
                    assertThat(node.isRootNode, equalTo(true))
                }

                it("is the same node as the node for the task container") {
                    assertThat(graph.nodeFor(taskContainer), equalTo(node))
                }
            }

            on("getting the node for container A") {
                val node = graph.nodeFor(containerA)

                it("depends on nothing") {
                    assertThat(node.dependsOn, isEmpty)
                }

                it("is depended on by the task container and container B") {
                    assertThat(node.dependedOnBy.mapToSet { it.container }, equalTo(setOf(taskContainer, containerB)))
                }

                it("indicates that it is not the root node of the graph") {
                    assertThat(node.isRootNode, equalTo(false))
                }
            }

            on("getting the node for container B") {
                val node = graph.nodeFor(containerB)

                it("depends on container A") {
                    assertThat(node.dependsOn.mapToSet { it.container }, equalTo(setOf(containerA)))
                }

                it("is depended on by the task container") {
                    assertThat(node.dependedOnBy.mapToSet { it.container }, equalTo(setOf(taskContainer)))
                }

                it("indicates that it is not the root node of the graph") {
                    assertThat(node.isRootNode, equalTo(false))
                }
            }
        }

        given("a task with a dependency which itself has a dependency") {
            val otherDependencyContainer = Container("other-dependency", imageSourceDoesNotMatter())
            val taskDependencyContainer = Container("task-dependency", imageSourceDoesNotMatter(), dependencies = setOf(otherDependencyContainer.name))
            val taskContainer = Container("some-container", imageSourceDoesNotMatter())
            val runConfig = TaskRunConfiguration(taskContainer.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig, dependsOnContainers = setOf(taskDependencyContainer.name))
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer, taskDependencyContainer, otherDependencyContainer))
            val graph = ContainerDependencyGraph(config, task)

            on("getting the task container node") {
                val node = graph.taskContainerNode

                it("depends on the task dependency") {
                    assertThat(node.dependsOn.mapToSet { it.container }, equalTo(setOf(taskDependencyContainer)))
                }

                it("is depended on by nothing") {
                    assertThat(node.dependedOnBy, isEmpty)
                }

                it("indicates that it is the root node of the graph") {
                    assertThat(node.isRootNode, equalTo(true))
                }

                it("is the same node as the node for the task container") {
                    assertThat(graph.nodeFor(taskContainer), equalTo(node))
                }
            }

            on("getting the node for the task dependency") {
                val node = graph.nodeFor(taskDependencyContainer)

                it("depends on its direct dependency") {
                    assertThat(node.dependsOn.mapToSet { it.container }, equalTo(setOf(otherDependencyContainer)))
                }

                it("is depended on by the task container") {
                    assertThat(node.dependedOnBy.mapToSet { it.container }, equalTo(setOf(taskContainer)))
                }

                it("indicates that it is not the root node of the graph") {
                    assertThat(node.isRootNode, equalTo(false))
                }
            }

            on("getting the node for the other container") {
                val node = graph.nodeFor(otherDependencyContainer)

                it("depends on nothing") {
                    assertThat(node.dependsOn, isEmpty)
                }

                it("is depended on by the task dependency") {
                    assertThat(node.dependedOnBy.mapToSet { it.container }, equalTo(setOf(taskDependencyContainer)))
                }

                it("indicates that it is not the root node of the graph") {
                    assertThat(node.isRootNode, equalTo(false))
                }
            }
        }

        given("a task with a dependency which is also a dependency of the task container") {
            val dependencyContainer = Container("dependency-container", imageSourceDoesNotMatter())
            val taskContainer = Container("some-container", imageSourceDoesNotMatter(), dependencies = setOf(dependencyContainer.name))
            val runConfig = TaskRunConfiguration(taskContainer.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig, dependsOnContainers = setOf(dependencyContainer.name))
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer))
            val graph = ContainerDependencyGraph(config, task)

            on("getting the task container node") {
                val node = graph.taskContainerNode

                it("depends on the dependency") {
                    assertThat(node.dependsOn.mapToSet { it.container }, equalTo(setOf(dependencyContainer)))
                }

                it("is depended on by nothing") {
                    assertThat(node.dependedOnBy, isEmpty)
                }

                it("indicates that it is the root node of the graph") {
                    assertThat(node.isRootNode, equalTo(true))
                }

                it("is the same node as the node for the task container") {
                    assertThat(graph.nodeFor(taskContainer), equalTo(node))
                }
            }

            on("getting the node for the dependency") {
                val node = graph.nodeFor(dependencyContainer)

                it("depends on nothing") {
                    assertThat(node.dependsOn, isEmpty)
                }

                it("is depended on by the task container") {
                    assertThat(node.dependedOnBy.mapToSet { it.container }, equalTo(setOf(taskContainer)))
                }

                it("indicates that it is not the root node of the graph") {
                    assertThat(node.isRootNode, equalTo(false))
                }
            }
        }

        given("a task with a container that has a dependency that does not exist") {
            val taskContainer = Container("some-container", imageSourceDoesNotMatter(), dependencies = setOf("non-existent-container"))
            val runConfig = TaskRunConfiguration(taskContainer.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig)
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ ContainerDependencyGraph(config, task) }, throws<ContainerDoesNotExistException>(withMessage("The container 'non-existent-container' referenced by container 'some-container' does not exist.")))
                }
            }
        }

        given("a task with a container that has a dependency that has a dependency that does not exist") {
            val dependencyContainer = Container("dependency-container", imageSourceDoesNotMatter(), dependencies = setOf("non-existent-container"))
            val taskContainer = Container("some-container", imageSourceDoesNotMatter(), dependencies = setOf(dependencyContainer.name))
            val runConfig = TaskRunConfiguration(taskContainer.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig)
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ ContainerDependencyGraph(config, task) }, throws<ContainerDoesNotExistException>(withMessage("The container 'non-existent-container' referenced by container 'dependency-container' does not exist.")))
                }
            }
        }

        given("a task with a container that has a dependency on itself") {
            val taskContainer = Container("some-container", imageSourceDoesNotMatter(), dependencies = setOf("some-container"))
            val runConfig = TaskRunConfiguration(taskContainer.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig)
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ ContainerDependencyGraph(config, task) }, throws<ContainerSelfDependencyException>(withMessage("The container 'some-container' cannot depend on itself.")))
                }
            }
        }

        given("a task with a container with a dependency that depends on itself") {
            val dependencyContainer = Container("dependency-container", imageSourceDoesNotMatter(), dependencies = setOf("dependency-container"))
            val taskContainer = Container("some-container", imageSourceDoesNotMatter(), dependencies = setOf(dependencyContainer.name))
            val runConfig = TaskRunConfiguration(taskContainer.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig)
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ ContainerDependencyGraph(config, task) }, throws<ContainerSelfDependencyException>(withMessage("The container 'dependency-container' cannot depend on itself.")))
                }
            }
        }

        given("a task with a container A that depends on container B, which itself depends on A") {
            val dependencyContainer = Container("container-b", imageSourceDoesNotMatter(), dependencies = setOf("container-a"))
            val taskContainer = Container("container-a", imageSourceDoesNotMatter(), dependencies = setOf("container-b"))
            val runConfig = TaskRunConfiguration(taskContainer.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig)
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ ContainerDependencyGraph(config, task) }, throws<ContainerDependencyCycleException>(withMessage("There is a dependency cycle in task 'the-task'. Container 'container-a' depends on 'container-b', which depends on 'container-a'.")))
                }
            }
        }

        given("a task with a container A that depends on container B, which depends on container C, which itself depends on A") {
            val containerA = Container("container-a", imageSourceDoesNotMatter(), dependencies = setOf("container-b"))
            val containerB = Container("container-b", imageSourceDoesNotMatter(), dependencies = setOf("container-c"))
            val containerC = Container("container-c", imageSourceDoesNotMatter(), dependencies = setOf("container-a"))
            val runConfig = TaskRunConfiguration(containerA.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig)
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(containerA, containerB, containerC))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ ContainerDependencyGraph(config, task) }, throws<ContainerDependencyCycleException>(withMessage("There is a dependency cycle in task 'the-task'. Container 'container-a' depends on 'container-b', which depends on 'container-c', which depends on 'container-a'.")))
                }
            }
        }

        given("a task which runs container A, and which has a dependency on container B, which depends on the task container A") {
            val containerA = Container("container-a", imageSourceDoesNotMatter())
            val containerB = Container("container-b", imageSourceDoesNotMatter(), dependencies = setOf(containerA.name))
            val runConfig = TaskRunConfiguration(containerA.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig, dependsOnContainers = setOf(containerB.name))
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(containerA, containerB))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ ContainerDependencyGraph(config, task) }, throws<ContainerDependencyCycleException>(withMessage("There is a dependency cycle in task 'the-task'. Container 'container-b' (which is explicitly started by the task) depends on the task container 'container-a'.")))
                }
            }
        }

        given("a task which runs container A, and which has a dependency on container B, which has a dependency on container C, which depends on the task container, container A") {
            val containerA = Container("container-a", imageSourceDoesNotMatter())
            val containerC = Container("container-c", imageSourceDoesNotMatter(), dependencies = setOf(containerA.name))
            val containerB = Container("container-b", imageSourceDoesNotMatter(), dependencies = setOf(containerC.name))
            val runConfig = TaskRunConfiguration(containerA.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig, dependsOnContainers = setOf(containerB.name))
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(containerA, containerB, containerC))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ ContainerDependencyGraph(config, task) }, throws<ContainerDependencyCycleException>(withMessage("There is a dependency cycle in task 'the-task'. Container 'container-b' (which is explicitly started by the task) depends on 'container-c', and 'container-c' depends on the task container 'container-a'.")))
                }
            }
        }

        given("a task which runs container A, and which has a dependency on container B, which has a dependency on container C, which has a dependency on container D, which depends on the task container, container A") {
            val containerA = Container("container-a", imageSourceDoesNotMatter())
            val containerD = Container("container-d", imageSourceDoesNotMatter(), dependencies = setOf(containerA.name))
            val containerC = Container("container-c", imageSourceDoesNotMatter(), dependencies = setOf(containerD.name))
            val containerB = Container("container-b", imageSourceDoesNotMatter(), dependencies = setOf(containerC.name))
            val runConfig = TaskRunConfiguration(containerA.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig, dependsOnContainers = setOf(containerB.name))
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(containerA, containerB, containerC, containerD))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ ContainerDependencyGraph(config, task) }, throws<ContainerDependencyCycleException>(withMessage("There is a dependency cycle in task 'the-task'. Container 'container-b' (which is explicitly started by the task) depends on 'container-c', and 'container-c' depends on 'container-d', and 'container-d' depends on the task container 'container-a'.")))
                }
            }
        }

        given("a task which runs container A, and which has a dependency on container B, which has a dependency on container C, which depends on container B") {
            val containerA = Container("container-a", imageSourceDoesNotMatter())
            val containerC = Container("container-c", imageSourceDoesNotMatter(), dependencies = setOf("container-b"))
            val containerB = Container("container-b", imageSourceDoesNotMatter(), dependencies = setOf(containerC.name))
            val runConfig = TaskRunConfiguration(containerA.name, Command.parse("some-command"))
            val task = Task("the-task", runConfig, dependsOnContainers = setOf(containerB.name))
            val config = TaskSpecialisedConfiguration("the-project", TaskMap(task), ContainerMap(containerA, containerB, containerC))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ ContainerDependencyGraph(config, task) }, throws<ContainerDependencyCycleException>(withMessage("There is a dependency cycle in task 'the-task'. Container 'container-b' (which is explicitly started by the task) depends on 'container-c', and 'container-c' depends on 'container-b'.")))
                }
            }
        }
    }
})

inline fun <T, R> Iterable<T>.mapToSet(transform: (T) -> R): Set<R> = this.map(transform).toSet()
