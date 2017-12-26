# `batect.yml` reference

Batect uses a YAML-based configuration file.

By convention, this file is called `batect.yml` and is placed in the root of your project (alongside the `batect` script).
You can, however, use a different name or location, and tell `batect` where to find it with the `-f` option.

## Sample configuration file

Taken from the [Java sample project](https://github.com/charleskorn/batect-sample-java):

```yaml
project_name: international-transfers-service

containers:
  build-env:
    image: openjdk:8u141-jdk
    volumes:
      - .:/code
      - .gradle-cache:/root/.gradle
    working_directory: /code
    environment:
      - GRADLE_OPTS=-Dorg.gradle.daemon=false

  database:
    build_directory: dev-infrastructure/database
    environment:
      - POSTGRES_USER=international-transfers-service
      - POSTGRES_PASSWORD=TheSuperSecretPassword
      - POSTGRES_DB=international-transfers-service

  exchange-rate-service:
    build_directory: dev-infrastructure/exchange-rate-service-fake

  international-transfers-service:
    build_directory: dev-infrastructure/international-transfers-service
    ports:
      - 6001:6001
    dependencies:
      - database
      - exchange-rate-service

tasks:
  build:
    description: Build the application.
    run:
      container: build-env
      command: sh -c './gradlew assembleDist && cp build/distributions/international-transfers-service.zip dev-infrastructure/international-transfers-service/app.zip'

  unitTest:
    description: Run the unit tests.
    run:
      container: build-env
      command: ./gradlew test

  continuousUnitTest:
    description: Run the unit tests and then re-run them when any code changes are detected.
    run:
      container: build-env
      command: ./gradlew --continuous test

  integrationTest:
    description: Run the integration tests.
    run:
      container: build-env
      command: ./gradlew integrationTest
    start:
      - database
      - exchange-rate-service

  journeyTest:
    description: Run the journey tests.
    run:
      container: build-env
      command: ./gradlew journeyTest
    start:
      - international-transfers-service
    prerequisites:
      - build

  app:
    description: Run the application.
    run:
      container: international-transfers-service
    prerequisites:
      - build
```

## Reference

The root of the configuration file is made up of:

* `project_name` The name of your project. Used to label any images built. **Required.**

* `containers` Definitions for each of the containers that make up your various environments. [See details below.](#container-definitions)

* `tasks` Definitions for each of your tasks. [See details below.](#task-definitions)

### Container definitions

Each container definition is made up of:

* `image` Image name (in standard Docker image reference format) to use for this container. **One of `image` or `build_directory` is required.**

* `build_directory` Path (relative to the configuration file's directory) to a directory containing a Dockerfile to build and use as an image for
  this container. **One of `image` or `build_directory` is required.**

* `command` Command to run when the container starts. If not provided, the default command for the image will be run. Both of these can be overridden
  for an individual task by specifying a `command` at the task level.

* `environment` List of environment variables (in `name=value` format) for the container.

  You can pass environment variables from the host (ie. where you run batect) to the container by using `$<name>`. For example, to set
  `SUPER_SECRET_PASSWORD` in the container to the value of the `MY_PASSWORD` variable on the host, use
  `SUPER_SECRET_PASSWORD=$MY_PASSWORD`. Substitutions in the middle of values is not supported (eg. `SUPER_SECRET_PASSWORD=My password is $MY_PASSWORD`
  will not work). Be careful when using this - by relying on the host's environment variables, you are introducing inconsistency to how the container
  runs between hosts, which is something you generally want to avoid.

  The `TERM` environment variable, if set on the host, is always automatically passed through to the container.

  Proxy-related environment variables, if set on the host, are passed through to the container at build and run time, but are not used for image pulls.
  If a proxy-related environment variable is defined on the container's configuration, it takes precedence over the host-provided value.
  See [this page](Proxies.md) for more information on using Docker with proxies and how batect handles proxies.

* `working_directory` Working directory to start the container in. If not provided, the default working directory for the image will be used.

* `volumes` List of volume mounts (in standard Docker `local:container` or `local:container:mode` format) to create for the container). Relative local
  paths will be resolved relative to the configuration file's directory.

* `ports` List of ports (in standard Docker `local:container` format) to make available to the host machine. For example, `123:456` will make port 456
  inside the container available on the host machine at port 123.

  Only TCP ports are supported at present.

  This does not affect the visibility of ports within the network created for all containers in a task - any container started as part of a task will be
  able to access any port on any other container at the address `container_name:container_port`. For example, if a process running in the `http-server`
  container listens on port 2000, any other container in the task can access that at `http-server:2000` without port 2000 being listed in `ports`.

* `dependencies` List of other containers that should be started and healthy before starting this container. If a dependency's image does not contain a
  [health check](https://docs.docker.com/engine/reference/builder/#healthcheck), then as soon as it has started, it is considered to be healthy.

* `health_check` Overrides for health check configuration specified in image or Dockerfile:
    * `retries` The number of times to perform the health check before considering the container unhealthy.
    * `interval` The interval between runs of the health check.
    * `start_period` The time to wait before failing health checks count against the retry count. The health check is still run during this period,
      and if the check succeeds, the container is immediately considered healthy.

### Task definitions

Each task definition is made up of:

* `description` Description shown when running `batect --list-tasks`.

* `run`:
    * `container` [Container](#container-definitions) to run for this task. **Required.**
    * `command` Command to run for this task. Overrides any command specified on the container definition and the default image command.
    * `environment` List of environment variables (in `name=value` format) for the container. If a variable is specified both here and on the container
      itself, the value given here will override the value defined on the container. Just like when specifying a variable directly on the container,
      you can pass variables from the host to the container in the `CONTAINER_VARIABLE=$HOST_VARIABLE` format.

* `start` List of other containers that should be started and healthy before starting the task container given in `run`. The behaviour is the same as the
  `dependencies` property on a container definition.

* `prerequisites` List of other tasks that should be run before running this task. If a prerequisite task finishes with a non-zero exit code, then neither
  this task nor any other prerequisites will be run.
