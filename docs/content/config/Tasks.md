# Task definitions

Each task definition is made up of:

## `description`
Description shown when running `batect --list-tasks`.

## `group`
Group name used to group tasks when running `batect --list-tasks`.

Available since v0.27.

## `run`
Specifies what to do when this task starts:

* `container` [Container](Containers.md) to run for this task. **Required.**

* `command` Command to run for this task.

    Overrides any command specified on the container definition and the image's default command. If no command is provided here,
    [the command specified on the container definition](Containers.md#command) is used if there is one, otherwise the image's default command is used.

    Just like when specifying a command for a container, this command is passed to the image's `ENTRYPOINT`, if there is one. This can prevent
    shell syntax features like `&&` from working. See the [note about entrypoints in the documentation for containers](Containers.md#command-entrypoint-note)
    for more information.

* `environment` List of environment variables (in `name: value` format) to pass to the container, in addition to those defined on the
  container itself.

    If a variable is specified both here and on the container itself, the value given here will override the value defined on the container.

    This field supports all of the same syntax as when [specifying a variable directly on the container](Containers.md#environment),
    including passing variables from the host to the container and providing defaults for when the host variable is not set.

    Prior to v0.21, environment variables were required to be supplied in `name=value` format.

* `ports` List of port mappings to create for the container, in addition to those defined on the container itself.

    Behaves identically to [specifying a port mapping directly on the container](Containers.md#ports), and supports the same syntax.

    Available since v0.13.

* `working_directory` Working directory to use for this task's container.

    Overrides any working directory on the container definition and the image's default working directory. If no working directory is provided here,
    [the working directory specified on the container definition](Containers.md#working_directory) is used if there is one, otherwise the image's default
    working directory is used.

    Available since v0.26.

## `dependencies`
List of other containers (not tasks) that should be started and healthy before starting the task container given in `run`.

The behaviour is the same as if the dependencies were specified for the `dependencies` property of the task's container's definition.

## `prerequisites`
List of other tasks that should be run to completion before running this task.

If a prerequisite task finishes with a non-zero exit code, then neither this task nor any other prerequisites will be run.

The tasks are run in the same order that they are declared in, unless reordering is required to satisfy the prerequisites of
of of this task's prerequisites.

## Examples

For more examples and real-world scenarios, take a look at the [sample projects](../SampleProjects.md).

### Minimal configuration
```yaml
tasks:
  start-app:
    run:
      container: app
```

Running the task `start-app` will start the `app` container.

The container will run the command provided in the container configuration (or the default command in the image if there is no command
given for the container definition).

### Task with prerequisites
```yaml
tasks:
  build:
    run:
      container: build-env
      command: build.sh

  start-app:
    run:
      container: app
    prerequisites:
      - build
```

Running the task `start-app` will first run the `build` task (which runs `build.sh` in the `build-env` container), and then run the `app` container.

If the command `build.sh` exits with a non-zero exit code, `start-app` will not be run.

### Task with dependencies
```yaml
tasks:
  start-app:
    run:
      container: app
    dependencies:
      - database
      - auth-service-fake
```

Running the task `start-app` will do the following:

1. Build or pull the images for the `app`, `database` and `auth-service-fake` containers, as appropriate
2. Start the `database` and `auth-service-fake` containers
3. Wait for the `database` and `auth-service-fake` containers to report themselves as healthy
   ([if they have health checks defined](../tips/WaitingForDependenciesToBeReady.md))
4. Start the `app` container

### Task with environment variables
```yaml
tasks:
  start-app:
    run:
      container: app
      environment:
        COUNTRY: Australia
        SUPER_SECRET_VALUE: $SECRET_PASSWORD
        ANOTHER_SECRET_VALUE: ${SECRET_PASSWORD}
        OPTIMISATION_LEVEL: ${HOST_OPTIMISATION_LEVEL:-none}
```

Running the task `start-app` will start the `app` container with the following environment variables:

* The environment variable `COUNTRY` will have value `Australia`.

* The environment variables `SUPER_SECRET_VALUE` and `ANOTHER_SECRET_VALUE` will have the value of the `SECRET_PASSWORD` environment variable on
  the host. (So, for example, if `SECRET_PASSWORD` is `abc123` on the host, then `SUPER_SECRET_VALUE` will have the value `abc123` in the container.)

    If `SECRET_PASSWORD` is not set on the host, batect will show an error message and not start the task.

* The environment variable `OPTIMISATION_LEVEL` will have the value of the `HOST_OPTIMISATION_LEVEL` environment variable on the host.

    If `HOST_OPTIMISATION_LEVEL` is not set on the host, then `OPTIMISATION_LEVEL` will have the value `none` in the container.

### Task with port mappings
```yaml
tasks:
  start-app:
    run:
      container: app
      ports:
        - 123:456
        - local: 1000
          container: 2000
```

Running the task `start-app` will start the `app` container with the following port mappings defined:

* Port 123 on the host will be mapped to port 456 inside the container
* Port 1000 on the host will be mapped to port 2000 inside the container

For example, this means that if a web server is listening on port 456 within the container, it can be accessed from the host at `http://localhost:123`.

The Dockerfile for the image used by the app container does not need to contain an `EXPOSE` instruction for ports 456 or 2000.

Note that this does not affect how containers launched by batect as part of the same task access ports used by each other, just how they're exposed to the host.
Any container started as part of a task will be able to access any port on any other container at the address `container_name:container_port`. For example,
if a process running in another container wants to access the application running on port 456 in the `app` container, it would access it at `app:456`,
not `app:123`.
