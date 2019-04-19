# Contribution Guide

Welcome and thanks for your interest in helping improve batect!

This guide is a work in progress. If there is some information that you'd like to see below,
please [submit an issue](https://github.com/charleskorn/batect/issues/new).

## I want to help out, what should I do?

It's entirely up to you, do whatever you're most interested in. Some suggestions:

* take a look at any open [issues](https://github.com/charleskorn/batect/issues?q=is%3Aopen+is%3Aissue), especially those tagged with
  ['good first issue'](https://github.com/charleskorn/batect/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22)
* take a look at the [roadmap](https://github.com/charleskorn/batect/blob/master/ROADMAP.md) and pick a feature you'd like to implement
* improve the [documentation](https://batect.charleskorn.com/) - add further examples, tips and tricks or clarify sections
* create a [sample project](https://batect.charleskorn.com/SampleProjects.html) for a language or framework that doesn't already have one

## Prerequisites

* JDK (version 8 or higher)
* Git (version 2.17.0 or higher)
* Docker (any version compatible with batect)

## Dev tooling

### Building the application

`./gradlew build` will compile the application, while `./gradlew installShadowDist` will compile and assemble batect (you can then invoke batect
with `./app/build/install/app-shadow/bin/batect`).

### Running the unit tests and linter

`./gradlew check`

Or, to run the tests and linter once and then automatically re-run them when the code changes:

`./gradlew --continuous check`

### Fixing linting issues

`./gradlew spotlessApply`

### Running the integration tests

`./gradlew integrationTest`

### Running the journey tests

`./gradlew journeyTest`

### Serve the docs locally

`./gradlew docs:serve`

## Pull request guidelines

All pull requests are welcome and warmly encouraged. Some things to keep in mind:

* all code changes should have accompanying unit tests (or, if it is not possible or reasonable to unit test the functionality or bugfix in question,
  one or more journey tests)
* the Travis build should pass - this will be triggered automatically when you submit your PR
* if you've added a new feature or changed the behaviour of an existing feature, please update the documentation to reflect this
* please keep PRs limited to a single bugfix or feature - if you would like to fix multiple issues or add multiple features, please submit a separate PR for each
* submitting work-in-progress PRs for feedback is welcome and encouraged

## Technical overview

### Task execution phases

When executing a task, the following steps are performed:

1. Parse the configuration file
2. Determine the order to run the task and its prerequisites in
3. For each task that needs to be executed:

    1. Create a dependency graph of the task's main container and its dependencies
    2. Create the Docker network for the task and prepare and start each of the dependencies, respecting the dependency constraints specified by the user
    3. Run the main container
    4. Clean up every container, temporary file, temporary directory and Docker network created

### Task execution model

Steps 3ii, 3iii and 3iv above are all executed using the task execution model. This model is made up of the following concepts:

* [**Step**](app/src/main/kotlin/batect/execution/model/steps): an operation to perform, such as pulling an image, creating the task network or removing a container.

* [**Stage**](app/src/main/kotlin/batect/execution/model/stages/Stage.kt): container for steps that need to occur during a phase of execution. There are two stages:

  * The run stage is responsible for everything required by 3ii and 3iii above: preparing all containers, starting all dependencies in the correct order and then
    running the main container.

  * The cleanup stage is responsible for everything required by 3iv above: cleaning up everything that was created during the run stage, even if the run stage was
    not successful.

* [**Stage planner**](app/src/main/kotlin/batect/execution/model/stages): determines what steps need to be run in a stage based on the task's dependency graph, the
  configuration of each container in the graph, and, in the case of the cleanup stage, what steps completed successfully during the run stage.

* [**Step rule**](app/src/main/kotlin/batect/execution/model/rules): determines when steps are ready to be executed, based on the task's dependency graph and what
  steps have completed successfully so far.

  For example, the rule for the 'start container' step will only allow the step to start if the image for the container has been pulled or built and all of the
  container's dependencies have reported as healthy.

* [**Event**](app/src/main/kotlin/batect/execution/model/events): things that have happened, such as a container being created, progress being made on an image pull
  or a temporary file being deleted. Every step emits at least one event to indicate its progress and eventual successful completion or failure.

* [**State machine**](app/src/main/kotlin/batect/execution/TaskStateMachine.kt): keeps track of the current state of the task, including which stage is currently
  running, what events have occurred and which steps can be executed.

* [**Execution manager**](app/src/main/kotlin/batect/execution/ParallelExecutionManager.kt): runs steps that are ready for execution.

* [**Event logger**](app/src/main/kotlin/batect/ui): presents the state of the startup and cleanup phases to the user based on the steps that have started and the
  events that have occurred. (Output from running the main task container itself is not sent through the event logger and is presented to the user directly.)
