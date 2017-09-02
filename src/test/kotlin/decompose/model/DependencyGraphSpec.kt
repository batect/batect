package decompose.model

import com.natpryce.hamkrest.assertion.assertThat
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
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DependencyGraphSpec : Spek({
    describe("a dependency graph") {
        given("a task with no dependencies") {
            val container = Container("some-container", "/build-dir")
            val runConfig = TaskRunConfiguration(container.name, "some-command")
            val task = Task("the-task", runConfig, dependencies = emptySet())
            val config = Configuration("the-project", TaskMap(task), ContainerMap(container))
            val graph = DependencyGraph(config, task)

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
                val otherContainer = Container("the-other-container", "does-not-matter")

                it("throws an exception") {
                    assertThat({ graph.nodeFor(otherContainer) }, throws<IllegalArgumentException>(withMessage("Container 'the-other-container' is not part of this dependency graph.")))
                }
            }
        }

        given("a task that refers to a container that does not exist") {
            val runConfig = TaskRunConfiguration("some-non-existent-container", "some-command")
            val task = Task("the-task", runConfig, dependencies = emptySet())
            val config = Configuration("the-project", TaskMap(task), ContainerMap())

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ DependencyGraph(config, task) }, throws<DependencyResolutionFailedException>(withMessage("The container 'some-non-existent-container' referenced by task 'the-task' does not exist.")))
                }
            }
        }

        given("a task with a dependency") {
            val taskContainer = Container("some-container", "/build-dir")
            val dependencyContainer = Container("dependency-container", "/other-dir")
            val runConfig = TaskRunConfiguration(taskContainer.name, "some-command")
            val task = Task("the-task", runConfig, dependencies = setOf(dependencyContainer.name))
            val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer))
            val graph = DependencyGraph(config, task)

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
            val taskContainer = Container("some-container", "/build-dir")
            val dependencyContainer1 = Container("dependency-container-1", "/other-dir")
            val dependencyContainer2 = Container("dependency-container-2", "/other-dir")
            val dependencyContainer3 = Container("dependency-container-3", "/other-dir")
            val runConfig = TaskRunConfiguration(taskContainer.name, "some-command")
            val task = Task("the-task", runConfig, dependencies = setOf(dependencyContainer1.name, dependencyContainer2.name, dependencyContainer3.name))
            val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer1, dependencyContainer2, dependencyContainer3))
            val graph = DependencyGraph(config, task)

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
            val taskContainer = Container("some-container", "/build-dir")
            val runConfig = TaskRunConfiguration(taskContainer.name, "some-command")
            val task = Task("the-task", runConfig, dependencies = setOf("non-existent-dependency"))
            val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ DependencyGraph(config, task) }, throws<DependencyResolutionFailedException>(withMessage("The container 'non-existent-dependency' referenced by task 'the-task' does not exist.")))
                }
            }
        }

        given("a task with a dependency on the task container") {
            val taskContainer = Container("some-container", "/build-dir")
            val runConfig = TaskRunConfiguration(taskContainer.name, "some-command")
            val task = Task("the-task", runConfig, dependencies = setOf(taskContainer.name))
            val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ DependencyGraph(config, task) }, throws<DependencyResolutionFailedException>(withMessage("The task 'the-task' cannot start the container 'some-container' and also run it.")))
                }
            }
        }

        given("a task with a container that has some direct dependencies") {
            val dependencyContainer1 = Container("dependency-container-1", "/other-dir")
            val dependencyContainer2 = Container("dependency-container-2", "/other-dir")
            val dependencyContainer3 = Container("dependency-container-3", "/other-dir")
            val taskContainer = Container("some-container", "/build-dir", dependencies = setOf(dependencyContainer1.name, dependencyContainer2.name, dependencyContainer3.name))
            val runConfig = TaskRunConfiguration(taskContainer.name, "some-command")
            val task = Task("the-task", runConfig)
            val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer1, dependencyContainer2, dependencyContainer3))
            val graph = DependencyGraph(config, task)

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
            val dependencyContainer1 = Container("dependency-container-1", "/other-dir")
            val dependencyContainer2 = Container("dependency-container-2", "/other-dir", dependencies = setOf(dependencyContainer1.name))
            val taskContainer = Container("some-container", "/build-dir", dependencies = setOf(dependencyContainer2.name))
            val runConfig = TaskRunConfiguration(taskContainer.name, "some-command")
            val task = Task("the-task", runConfig)
            val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer1, dependencyContainer2))
            val graph = DependencyGraph(config, task)

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
            val containerWithNoDependencies = Container("no-dependencies", "/other-dir")
            val dependencyContainer1 = Container("dependency-container-1", "/other-dir", dependencies = setOf(containerWithNoDependencies.name))
            val dependencyContainer2 = Container("dependency-container-2", "/other-dir", dependencies = setOf(containerWithNoDependencies.name))
            val taskContainer = Container("some-container", "/build-dir", dependencies = setOf(dependencyContainer1.name, dependencyContainer2.name))
            val runConfig = TaskRunConfiguration(taskContainer.name, "some-command")
            val task = Task("the-task", runConfig)
            val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer1, dependencyContainer2, containerWithNoDependencies))
            val graph = DependencyGraph(config, task)

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
            val containerA = Container("container-a", "/other-dir")
            val containerB = Container("container-b", "/other-dir", dependencies = setOf(containerA.name))
            val taskContainer = Container("some-container", "/build-dir", dependencies = setOf(containerB.name, containerA.name))
            val runConfig = TaskRunConfiguration(taskContainer.name, "some-command")
            val task = Task("the-task", runConfig)
            val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer, containerB, containerA))
            val graph = DependencyGraph(config, task)

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
            val otherDependencyContainer = Container("other-dependency", "/other-dir")
            val taskDependencyContainer = Container("task-dependency", "/other-dir", dependencies = setOf(otherDependencyContainer.name))
            val taskContainer = Container("some-container", "/build-dir")
            val runConfig = TaskRunConfiguration(taskContainer.name, "some-command")
            val task = Task("the-task", runConfig, dependencies = setOf(taskDependencyContainer.name))
            val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer, taskDependencyContainer, otherDependencyContainer))
            val graph = DependencyGraph(config, task)

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
            val dependencyContainer = Container("dependency-container", "/other-dir")
            val taskContainer = Container("some-container", "/build-dir", dependencies = setOf(dependencyContainer.name))
            val runConfig = TaskRunConfiguration(taskContainer.name, "some-command")
            val task = Task("the-task", runConfig, dependencies = setOf(dependencyContainer.name))
            val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer))
            val graph = DependencyGraph(config, task)

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
            val taskContainer = Container("some-container", "/build-dir", dependencies = setOf("non-existent-container"))
            val runConfig = TaskRunConfiguration(taskContainer.name, "some-command")
            val task = Task("the-task", runConfig)
            val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ DependencyGraph(config, task) }, throws<DependencyResolutionFailedException>(withMessage("The container 'non-existent-container' referenced by container 'some-container' does not exist.")))
                }
            }
        }

        given("a task with a container that has a dependency that has a dependency that does not exist") {
            val dependencyContainer = Container("dependency-container", "/build-dir", dependencies = setOf("non-existent-container"))
            val taskContainer = Container("some-container", "/build-dir", dependencies = setOf(dependencyContainer.name))
            val runConfig = TaskRunConfiguration(taskContainer.name, "some-command")
            val task = Task("the-task", runConfig)
            val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ DependencyGraph(config, task) }, throws<DependencyResolutionFailedException>(withMessage("The container 'non-existent-container' referenced by container 'dependency-container' does not exist.")))
                }
            }
        }

        given("a task with a container that has a dependency on itself") {
            val taskContainer = Container("some-container", "/build-dir", dependencies = setOf("some-container"))
            val runConfig = TaskRunConfiguration(taskContainer.name, "some-command")
            val task = Task("the-task", runConfig)
            val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ DependencyGraph(config, task) }, throws<DependencyResolutionFailedException>(withMessage("The container 'some-container' cannot depend on itself.")))
                }
            }
        }

        given("a task with a container with a dependency that depends on itself") {
            val dependencyContainer = Container("dependency-container", "/build-dir", dependencies = setOf("dependency-container"))
            val taskContainer = Container("some-container", "/build-dir", dependencies = setOf(dependencyContainer.name))
            val runConfig = TaskRunConfiguration(taskContainer.name, "some-command")
            val task = Task("the-task", runConfig)
            val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ DependencyGraph(config, task) }, throws<DependencyResolutionFailedException>(withMessage("The container 'dependency-container' cannot depend on itself.")))
                }
            }
        }

        given("a task with a container A that depends on container B, which itself depends on A") {
            val dependencyContainer = Container("container-b", "/build-dir", dependencies = setOf("container-a"))
            val taskContainer = Container("container-a", "/build-dir", dependencies = setOf("container-b"))
            val runConfig = TaskRunConfiguration(taskContainer.name, "some-command")
            val task = Task("the-task", runConfig)
            val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ DependencyGraph(config, task) }, throws<DependencyResolutionFailedException>(withMessage("There is a dependency cycle in task 'the-task'. Container 'container-a' depends on 'container-b', which depends on 'container-a'.")))
                }
            }
        }

        given("a task with a container A that depends on container B, which depends on container C, which itself depends on A") {
            val containerA = Container("container-a", "/build-dir", dependencies = setOf("container-b"))
            val containerB = Container("container-b", "/build-dir", dependencies = setOf("container-c"))
            val containerC = Container("container-c", "/build-dir", dependencies = setOf("container-a"))
            val runConfig = TaskRunConfiguration(containerA.name, "some-command")
            val task = Task("the-task", runConfig)
            val config = Configuration("the-project", TaskMap(task), ContainerMap(containerA, containerB, containerC))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ DependencyGraph(config, task) }, throws<DependencyResolutionFailedException>(withMessage("There is a dependency cycle in task 'the-task'. Container 'container-a' depends on 'container-b', which depends on 'container-c', which depends on 'container-a'.")))
                }
            }
        }

        given("a task which runs container A, and which has a dependency on container B, which depends on the task container A") {
            val containerA = Container("container-a", "/build-dir")
            val containerB = Container("container-b", "/build-dir", dependencies = setOf(containerA.name))
            val runConfig = TaskRunConfiguration(containerA.name, "some-command")
            val task = Task("the-task", runConfig, dependencies = setOf(containerB.name))
            val config = Configuration("the-project", TaskMap(task), ContainerMap(containerA, containerB))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ DependencyGraph(config, task) }, throws<DependencyResolutionFailedException>(withMessage("There is a dependency cycle in task 'the-task'. Container 'container-b' (which is explicitly started by the task) depends on the task container 'container-a'.")))
                }
            }
        }

        given("a task which runs container A, and which has a dependency on container B, which has a dependency on container C, which depends on the task container, container A") {
            val containerA = Container("container-a", "/build-dir")
            val containerC = Container("container-c", "/build-dir", dependencies = setOf(containerA.name))
            val containerB = Container("container-b", "/build-dir", dependencies = setOf(containerC.name))
            val runConfig = TaskRunConfiguration(containerA.name, "some-command")
            val task = Task("the-task", runConfig, dependencies = setOf(containerB.name))
            val config = Configuration("the-project", TaskMap(task), ContainerMap(containerA, containerB, containerC))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ DependencyGraph(config, task) }, throws<DependencyResolutionFailedException>(withMessage("There is a dependency cycle in task 'the-task'. Container 'container-b' (which is explicitly started by the task) depends on 'container-c', and 'container-c' depends on the task container 'container-a'.")))
                }
            }
        }

        given("a task which runs container A, and which has a dependency on container B, which has a dependency on container C, which has a dependency on container D, which depends on the task container, container A") {
            val containerA = Container("container-a", "/build-dir")
            val containerD = Container("container-d", "/build-dir", dependencies = setOf(containerA.name))
            val containerC = Container("container-c", "/build-dir", dependencies = setOf(containerD.name))
            val containerB = Container("container-b", "/build-dir", dependencies = setOf(containerC.name))
            val runConfig = TaskRunConfiguration(containerA.name, "some-command")
            val task = Task("the-task", runConfig, dependencies = setOf(containerB.name))
            val config = Configuration("the-project", TaskMap(task), ContainerMap(containerA, containerB, containerC, containerD))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ DependencyGraph(config, task) }, throws<DependencyResolutionFailedException>(withMessage("There is a dependency cycle in task 'the-task'. Container 'container-b' (which is explicitly started by the task) depends on 'container-c', and 'container-c' depends on 'container-d', and 'container-d' depends on the task container 'container-a'.")))
                }
            }
        }

        given("a task which runs container A, and which has a dependency on container B, which has a dependency on container C, which depends on container B") {
            val containerA = Container("container-a", "/build-dir")
            val containerC = Container("container-c", "/build-dir", dependencies = setOf("container-b"))
            val containerB = Container("container-b", "/build-dir", dependencies = setOf(containerC.name))
            val runConfig = TaskRunConfiguration(containerA.name, "some-command")
            val task = Task("the-task", runConfig, dependencies = setOf(containerB.name))
            val config = Configuration("the-project", TaskMap(task), ContainerMap(containerA, containerB, containerC))

            on("creating the graph") {
                it("throws an exception") {
                    assertThat({ DependencyGraph(config, task) }, throws<DependencyResolutionFailedException>(withMessage("There is a dependency cycle in task 'the-task'. Container 'container-b' (which is explicitly started by the task) depends on 'container-c', and 'container-c' depends on 'container-b'.")))
                }
            }
        }
    }
})

inline fun <T, R> Iterable<T>.mapToSet(transform: (T) -> R): Set<R> = this.map(transform).toSet()
