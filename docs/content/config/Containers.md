# Container definitions

Each container definition is made up of:

## `image`
Image name (in standard Docker image reference format) to use for this container. **One of `image` or `build_directory` is required.**

!!! tip
    It is highly recommended that you specify a specific image version, and not use `latest`, to ensure that the same image is used
    everywhere. For example, use `alpine:3.7`, not `alpine` or `alpine:latest`.

## `build_directory`
Path (relative to the configuration file's directory) to a directory containing a Dockerfile to build and use as an image for this container.
**One of `image` or `build_directory` is required.**

## `build_args`
List of build args (in `name: value` format) to use when building the image in [`build_directory`](#build_directory).

Each build arg must be defined in the Dockerfile with an `ARG` instruction otherwise the value provided will have no effect.

The value of environment variables from the host can be passed as build args using [the same syntax as for `environment`](#environment-variable-substitution).

!!! warning
    Use caution when using build args for secret values. Build arg values can be revealed by anyone with a copy of the image with the `docker history` command.

Available since v0.28. The ability to use environment variable values in build args was added in v0.32.

## `dockerfile`
Dockerfile (relative to [`build_directory`](#build_directory)) to use when building the image in [`build_directory`](#build_directory). Defaults to `Dockerfile` if not set.

The Dockerfile must be within [`build_directory`](#build_directory).

Available since v0.31.

## `command`
Command to run when the container starts.

If not provided, the default command for the image will be run.

Both of these can be overridden for an individual task by specifying a [`command` at the task level](Tasks.md#run).

<a name="command-entrypoint-note"></a>

!!! note
    Keep in mind that this command is passed to the image's `ENTRYPOINT`, just like it would when using `docker run <image> <command>`
    directly.

    This means that if the entrypoint is not set or is not a shell, standard shell syntax features like `&&` might not work.

    See the Docker docs for [`CMD`](https://docs.docker.com/engine/reference/builder/#cmd) and
    [`ENTRYPOINT`](https://docs.docker.com/engine/reference/builder/#entrypoint) for more details.

    If you would like to use shell syntax features in your command, you have two options:

    1. Set the entrypoint in the image to a shell. For example:
       ```dockerfile
       ENTRYPOINT ["/bin/sh", "-c"]
       ```
    2. Wrap your command in a shell invocation.

        For example, if your command is `echo hello && echo world`, set `command` to `sh -c 'echo hello && echo world'`.

## `environment`
List of environment variables (in `name: value` format) for the container.

Prior to v0.21, environment variables were required to be supplied in `name=value` format.

### Environment variable substitution
You can pass environment variables from the host (ie. where you run batect) to the container by using any of the following formats:

* `$<name>` or `${<name>}`: use the value of `<name>` from the host as the value inside the container.

    If the referenced host variable is not present, batect will show an error message and not start the task.

* `${<name>:-<default>}`: use the value of `<name>` from the host as the value inside the container.

    If the referenced host variable is not present, `<default>` is used instead.

    `<default>` can be empty, so `${<name>:-}` will use the value of `<name>` from the host if it is
    set, or a blank value if it is not set.

For example, to set `SUPER_SECRET_PASSWORD` in the container to the value of the `MY_PASSWORD` variable on the host, use
`SUPER_SECRET_PASSWORD: $MY_PASSWORD` or `SUPER_SECRET_PASSWORD: ${MY_PASSWORD}`. Or, to default it to `insecure` if
`MY_PASSWORD` is not set, use `SUPER_SECRET_PASSWORD: ${MY_PASSWORD:-insecure}`.

Substitution in the middle of values is not supported (eg. `SUPER_SECRET_PASSWORD: My password is $MY_PASSWORD` will not work).

!!! warning
    Be careful when using this - by relying on the host's environment variables, you are introducing inconsistency to how the container
    runs between hosts, which is something you generally want to avoid.

The curly brace syntax for environment variables, including the ability to specify default values for environment variables,
was added in v0.21.

### `TERM`
The `TERM` environment variable, if set on the host, is always automatically passed through to the container. This ensures that features such as
coloured output continue to work correctly inside the container.

### Proxy-related environment variables
Proxy-related environment variables, if set on the host, are passed through to the container at build and run time, but are not used for image pulls.

If a proxy-related environment variable is defined on the container's configuration, it takes precedence over the host-provided value.

See [this page](../tips/Proxies.md) for more information on using batect with proxies.

## `working_directory`
Working directory to start the container in.

If not provided, the default working directory for the image will be used.

Both of these can be overridden for an individual task by specifying a [`working_directory` at the task level](Tasks.md#run).

## `volumes`
List of volume mounts to create for the container.

Relative local paths will be resolved relative to the configuration file's directory.

Two formats are supported:

* Standard Docker `local:container` or `local:container:mode` format

* An expanded format:
  ```yaml
  containers:
    my-container:
      ...
      volumes:
        # This is equivalent to .:/code:cached
        - local: .
          container: /code
          options: cached
  ```

See [this page](../tips/Performance.md#io-performance) for more information on why using `cached` volume mounts may be worthwhile.

## `ports`
List of ports to make available to the host machine.

Only TCP ports are supported at present.

Note that this does not affect how containers launched by batect as part of the same task access ports used by each other. Any container started as part of a
task will be able to access any port on any other container at the address `container_name:container_port`. For example, if a process running in the `http-server`
container listens on port 2000, any other container in the task can access that at `http-server:2000` without port 2000 being listed in `ports` (or an `EXPOSE`
Dockerfile instruction).

Two formats are supported:

* Standard Docker `local:container` format. For example, `1234:5678` will make port 5678 inside the container available on the host machine at port 1234.

* An expanded format:
  ```yaml
  containers:
    my-container:
      ...
      ports:
        # This is equivalent to 1234:5678
        - local: 1234
          container: 5678
  ```

## `dependencies`
List of other containers that should be started and healthy before starting this container.

If a dependency's image does not contain a [health check](https://docs.docker.com/engine/reference/builder/#healthcheck), then as soon as it has started,
it is considered to be healthy.

See [this page](../tips/WaitingForDependenciesToBeReady.md) for more information on how to ensure dependencies are ready before starting containers that
depend on them.

## `health_check`
Overrides [health check](https://docs.docker.com/engine/reference/builder/#healthcheck) configuration specified in the image or Dockerfile:

* `retries` The number of times to perform the health check before considering the container unhealthy.

* `interval` The interval between runs of the health check. Accepts values such as `2s` (two seconds) or `1m` (one minute).

* `start_period` The time to wait before failing health checks count against the retry count. The health check is still run during this period,
  and if the check succeeds, the container is immediately considered healthy. Accepts values such as `2s` (two seconds) or `1m` (one minute).

## `run_as_current_user`
Run the container with the same UID and GID as the user running batect (rather than the user the Docker daemon runs as, which is root
on Linux). This means that any files created by the container will be owned by the user running batect, rather than root.

This is really only useful on Linux. On OS X, the Docker daemon runs as the currently logged-in user and so any files created in the container are owned
by that user, so this is less of an issue. However, for consistency, the same configuration changes are made on both Linux and OS X.

`run_as_current_user` has the following options:

* `enabled` Defaults to `false`, set to `true` to enable 'run as current user' mode.

* `home_directory` Directory to use as home directory for user inside container. Required if `enabled` is `true`, not allowed if `enabled` is not provided
  or set to `false`.

  This directory is automatically created by batect with the correct owner and group.

!!! warning
    If the directory given by `home_directory` already exists inside the image for this container, it is overwritten.

See [this page](../tips/BuildArtifactsOwnedByRoot.md) for more information on the effects of this option and why it is necessary.

## `privileged`
Set to `true` to run the container in [privileged mode](https://docs.docker.com/engine/reference/commandline/run/#full-container-capabilities---privileged).

See also [`capabilities_to_add` and `capabilities_to_drop`](#capabilities_to_add-and-capabilities_to_drop).

Available since v0.29.

## `capabilities_to_add` and `capabilities_to_drop`
List of [capabilities](http://man7.org/linux/man-pages/man7/capabilities.7.html) to add or drop for the container.

This is equivalent to passing [`--cap-add` or `--cap-drop`](https://docs.docker.com/engine/reference/run/#runtime-privilege-and-linux-capabilities) to `docker run`.

Available since v0.31.

## `enable_init_process`
Set to `true` to pass the [`--init`](https://docs.docker.com/engine/reference/run/#specify-an-init-process) flag when running the container.
This creates the container with a simple PID 1 process to handle the responsibilities of the init system, which is required for some applications to behave correctly.

[Read this article](https://engineeringblog.yelp.com/2016/01/dumb-init-an-init-for-docker.html) if you're interested in more information about the behaviour
of different processes running as PID 1 and why this flag was introduced.

Available since v0.30.

## Examples

For more examples and real-world scenarios, take a look at the [sample projects](../SampleProjects.md).

### Minimal configuration with existing image
```yaml
containers:
  build-env:
    image: openjdk:8u141-jdk
```

Running the container `build-env` will launch a container that uses the `openjdk:8u141-jdk` image.

If the image has not already been pulled, batect will pull it before starting the container.

### Minimal configuration with Dockerfile
```yaml
containers:
  build-env:
    build_directory: .batect/build-env
```

Running the container `build-env` will first build the Dockerfile in the `.batect/build-env` directory, then run the resulting image.

The [Docker build cache](https://docs.docker.com/develop/develop-images/dockerfile_best-practices/#build-cache) is used during the build process,
so if the image definition has not changed since the last build, the image will not be rebuilt, saving time.

### Container with custom command
```yaml
containers:
  build-env:
    image: ruby:2.4.3
    command: echo 'Hello world'
```

Running the container `build-env` will run the command `echo 'Hello world'`, and not the default command specified in the `ruby:2.4.3` image.

This command could, however, be overridden by specifying a [`command` at the task level](Tasks.md#run).

### Container with environment variables
```yaml
containers:
  build-env:
    image: ruby:2.4.3
    environment:
      COUNTRY: Australia
      SUPER_SECRET_VALUE: $SECRET_PASSWORD
      ANOTHER_SECRET_VALUE: ${SECRET_PASSWORD}
      OPTIMISATION_LEVEL: ${HOST_OPTIMISATION_LEVEL:-none}
```

Running the container `build-env` will launch a container that uses the `ruby:2.4.3` image with the following environment variables:

* The environment variable `COUNTRY` will have value `Australia`.

* The environment variables `SUPER_SECRET_VALUE` and `ANOTHER_SECRET_VALUE` will have the value of the `SECRET_PASSWORD` environment variable on
  the host. (So, for example, if `SECRET_PASSWORD` is `abc123` on the host, then `SUPER_SECRET_VALUE` will have the value `abc123` in the container.)

    If `SECRET_PASSWORD` is not set on the host, batect will show an error message and not start the task.

* The environment variable `OPTIMISATION_LEVEL` will have the value of the `HOST_OPTIMISATION_LEVEL` environment variable on the host.

    If `HOST_OPTIMISATION_LEVEL` is not set on the host, then `OPTIMISATION_LEVEL` will have the value `none` in the container.

These environment variables could be overridden (and added to) with [`environment` at the task level](Tasks.md#run).

### Container with working directory
```yaml
containers:
  build-env:
    image: ruby:2.4.3
    working_directory: /somewhere
```

Running the container `build-env` will launch a container that uses the `ruby:2.4.3` image with the working directory set to `/somewhere`.

### Container with volume mounts
```yaml
containers:
  build-env:
    image: ruby:2.4.3
    volumes:
      - local: .
        container: /code
        options: cached
```

Running the container `build-env` will launch a container that uses the `ruby:2.4.3` image, with the directory containing the batect configuration file
mounted into the container at `/code`.

For example, if the batect configuration file is on the host at `/home/alice/code/my-project/batect.yml`, then `/home/alice/code/my-project` will be
available inside the container at `/code`.

See [this page](../tips/Performance.md#io-performance) for more information on why using `cached` volume mounts may be worthwhile.

### Container with ports
```yaml
containers:
  build-env:
    image: ruby:2.4.3
    ports:
      - local: 123
        container: 456
```

Running the container `build-env` will launch a container that uses the `ruby:2.4.3` image, with the port 123 on the host mapped to port 456 inside the
container. For example, this means that if a web server is listening on port 456 within the container, it can be accessed from the host at `http://localhost:123`.

The Dockerfile for the `ruby:2.4.3` image does not need to contain an `EXPOSE` instruction for port 456.

Note that this does not affect how containers launched by batect as part of the same task access ports used by each other, just how they're exposed to the host.
Any container started as part of a task will be able to access any port on any other container at the address `container_name:container_port`. For example,
if a process running in another container wants to access the application running on port 456 in the `build-env` container, it would access it at `build-env:456`,
not `build-env:123`.

### Container with dependencies
```yaml
containers:
  application:
    build_directory: .batect/application
    dependencies:
      - database

  database:
    build_directory: .batect/database
```

Running the container `application` will first run the `database` container and [wait for it to become healthy](../tips/WaitingForDependenciesToBeReady.md)
before starting the `application` container.

### Container that runs as the current user
```yaml
containers:
  build-env:
    image: ruby:2.4.3
    run_as_current_user:
      enabled: true
      home_directory: /home/container-user
```

Running the container `build-env` will launch a container that uses the `ruby:2.4.3` image with [run as current user mode](../tips/BuildArtifactsOwnedByRoot.md)
enabled.

### Container that runs with a Docker's default init process enabled
```yaml
containers:
  build-env:
    image: node:10.10.0-alpine
    volumes:
      - local: .
        container: /code
        options: cached
    enable_init_process: true
```

Running the container `build-env` will launch a container that uses the `node:10.10.0-alpine` image with Docker's default init process as PID 1.
