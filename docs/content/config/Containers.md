# Container definitions

!!! note
    This page reflects the options available in the [most recent version](https://github.com/batect/batect/releases/latest)
    of batect.

There are a number of configuration options for containers:

* [`image`](#image): image to use for this container. **One of `image` or `build_directory` is required.**
* [`build_directory`](#build_directory): path to a directory containing a Dockerfile to build and use for this container. **One of `image` or `build_directory` is required.**
* [`build_args`](#build_args): list of build args to use when building the image in `build_directory`
* [`dockerfile`](#dockerfile): Dockerfile to use when building the image in `build_directory`
* [`command`](#command): command to run when the container starts
* [`entrypoint`](#entrypoint): entrypoint to use to run the container's command
* [`environment`](#environment): environment variables for the container
* [`working_directory`](#working_directory): working directory for the container's command
* [`volumes`](#volumes): volume mounts to create for the container
* [`devices`](#devices): device mounts to create for the container
* [`ports`](#ports): ports to expose from the container to the host machine
* [`dependencies`](#dependencies): other containers to start before starting this container
* [`health_check`](#health_check): health check configuration for the container
* [`run_as_current_user`](#run_as_current_user): configuration for ['run as current user' mode](../tips/BuildArtifactsOwnedByRoot.md)
* [`setup_commands`](#setup_commands): commands to run inside the container after it has become healthy but before dependent containers start
* [`privileged`](#privileged): enable privileged mode for the container
* [`capabilities_to_add`](#capabilities_to_add-and-capabilities_to_drop): additional capabilities to grant to the container
* [`capabilities_to_drop`](#capabilities_to_add-and-capabilities_to_drop): additional capabilities to remove from the container
* [`enable_init_process`](#enable_init_process): enable Docker's init process for the container
* [`additional_hostnames`](#additional_hostnames): other hostnames to associate with the container, in addition to the container's name
* [`additional_hosts`](#additional_hosts): extra entries to add to `/etc/hosts` inside the container
* [`log_driver`](#log_driver): Docker log driver to use when running the container
* [`log_options`](#log_options): additional options for the log driver in use

## `additional_hostnames`
<small>**Equivalent Docker CLI option**: `--network-alias` to `docker run`, **equivalent Docker Compose option**: `extra_hosts`</small>

List of hostnames to associate with this container, in addition to the default hostname (the name of the container).

For example, `my-container` will be reachable by other containers running as part of the same task at both `my-container` and `other-name` with the following configuration:

```yaml
containers:
  my-container:
    additional_hostnames:
      - other-name
```

## `additional_hosts`
<small>**Equivalent Docker CLI option**: `--add-host` to `docker run`, **equivalent Docker Compose option**: `networks.aliases`</small>

Additional hostnames to add to `/etc/hosts` in the container. Equivalent to `--add-host` option for `docker run`.

For example, to configure processes inside `my-container` to resolve `database.example.com` to `1.2.3.4`:

```yaml
containers:
  my-container:
    additional_hosts:
      database.example.com: 1.2.3.4
```

## `build_args`
<small>**Equivalent Docker CLI option**: `--build-arg` to `docker build`, **equivalent Docker Compose option**: `build.args`</small>

List of build args (in `name: value` format) to use when building the image in [`build_directory`](#build_directory). Values can be [expressions](Overview.md#expressions).

Each build arg must be defined in the Dockerfile with an `ARG` instruction otherwise the value provided will have no effect.

!!! warning
    Use caution when using build args for secret values. Build arg values can be revealed by anyone with a copy of the image with the `docker history` command.

For example, to set the `message` build arg to `hello`:

```yaml
containers:
  my-container:
    build_args:
      message: hello
```

## `build_directory`
<small>**Equivalent Docker CLI option**: argument to `docker build`, **equivalent Docker Compose option**: `build` or `build.context`</small>

Path (relative to the configuration file's directory) to a directory containing a Dockerfile to build and use as an image for this container.
**One of `image` or `build_directory` is required.**

On Windows, `build_directory` can use either Windows-style (`path\to\thing`) or Unix-style (`path/to/thing`) paths, but for compatibility
with users running on other operating systems, using Unix-style paths is recommended.

The image can be overridden when running a task with [`--override-image`](../CLIReference.md#override-the-image-used-by-a-container-override-image).

The [Docker build cache](https://docs.docker.com/develop/develop-images/dockerfile_best-practices/#build-cache) is used during the build process,
so if the image definition has not changed since the last build, the image will not be rebuilt, saving time.

For example, running the container `my_container` from the following configuration will first build the Dockerfile in the `.batect/my-container` directory, then run the resulting image:

```yaml
containers:
  my-container:
    build_directory: .batect/my-container
```

## `capabilities_to_add` and `capabilities_to_drop`
<small>**Equivalent Docker CLI option**: `--cap-add` / `--cap-drop` to `docker run`, **equivalent Docker Compose option**: `cap_add` / `cap_drop`</small>

List of [capabilities](http://man7.org/linux/man-pages/man7/capabilities.7.html) to add or drop for the container.

For example:

```yaml
containers:
  my-container:
    capabilities_to_add:
      - CAP_SYS_ADMIN
    capabilities_to_drop:
      - CAP_KILL
```

## `command`
<small>**Equivalent Docker CLI option**: argument to `docker run`, **equivalent Docker Compose option**: `command`</small>

Command to run when the container starts.

If not provided, the default command for the image will be run.

Both of these can be overridden for an individual task by specifying a [`command` at the task level](Tasks.md#run).

For example, running the container `my-container` from the following configuration will run the command `echo 'Hello world'`, and not the default command specified in the `my-image` image:

```yaml
containers:
  my-container:
    image: my-image
    command: echo 'Hello world'
```

<a name="command-entrypoint-note"></a>

!!! note
    Keep in mind that this command is passed to the image's `ENTRYPOINT`, just like it would when using `docker run <image> <command>`
    directly.

    This means that if the entrypoint is not set or is not a shell, standard shell syntax features like `$MY_ENVIRONMENT_VARIABLE` and `&&` might not work.

    See the Docker docs for [`CMD`](https://docs.docker.com/engine/reference/builder/#cmd) and
    [`ENTRYPOINT`](https://docs.docker.com/engine/reference/builder/#entrypoint) for more details.

    If you would like to use shell syntax features in your command, you have four options:

    1. Create a shell script and invoke that instead of specifying the command directly.

    2. Wrap your command in a shell invocation.

        For example, if your command is `echo hello && echo world`, set `command` to `sh -c 'echo hello && echo world'`.

    3. Set the entrypoint in the image to a shell. For example:
       ```dockerfile
       ENTRYPOINT ["/bin/sh", "-c"]
       ```

    4. Set the [entrypoint](#entrypoint) for the container to a shell. For example:
       ```yaml
       containers:
         container-1:
           command: "'echo hello && echo world'" # Single quotes so that whole command is treated as a single argument when passed to sh, double quotes so that YAML preserves the single quotes
           entrypoint: /bin/sh -c
       ```

    Note that for both options 3 and 4, you must quote the command so that it is passed to `sh -c` as a single argument (we want the final command line to be `sh -c 'echo hello && echo world'`, not
    `sh -c echo hello && echo world`).

## `dependencies`
<small>**Equivalent Docker CLI option**: none, **equivalent Docker Compose option**: `depends_on` (behaviour differs)</small>

List of other containers that should be started and healthy before starting this container.

If a dependency's image does not contain a [health check](https://docs.docker.com/engine/reference/builder/#healthcheck), then as soon as it has started,
it is considered to be healthy.

See [this page](../tips/WaitingForDependenciesToBeReady.md) for more information on how to ensure dependencies are ready before starting containers that
depend on them.

For example, running the container `application` from the following configuration will first run the `database` container and [wait for it to become healthy](../tips/WaitingForDependenciesToBeReady.md)
before starting the `application` container:

```yaml
containers:
  application:
    build_directory: .batect/application
    dependencies:
      - database

  database:
    build_directory: .batect/database
```

## `devices`
<small>**Equivalent Docker CLI option**: `--device` to `docker run`, **equivalent Docker Compose option**: `devices`</small>

List of device mounts to create for the container.

Two formats are supported:

* `local:container` or `local:container:options` format

* An expanded format:
  ```yaml
  containers:
    my-container:
      ...
      devices:
      # This is equivalent to /dev/sda:/dev/disk:r
      - local: /dev/sda
        container: /dev/disk
        options: r
  ```

Note that the `local` device mounts will be different for Windows and Unix-like hosts. See the [Docker guide for adding host devices to containers](https://docs.docker.com/engine/reference/commandline/run/#add-host-device-to-container---device) for more information.

## `dockerfile`
<small>**Equivalent Docker CLI option**: `--file` to `docker build`, **equivalent Docker Compose option**: `build.dockerfile`</small>

Dockerfile (relative to [`build_directory`](#build_directory)) to use when building the image in [`build_directory`](#build_directory). Defaults to `Dockerfile` if not set.

The Dockerfile must be within [`build_directory`](#build_directory).

`dockerfile` must always be specified with Unix-style (`path/to/thing`) paths, even when running on Windows.

For example, running the container `my_container` from the following configuration will build the image given by the Dockerfile stored at `.batect/my-container/my-custom-Dockerfile`:

```yaml
containers:
  my-container:
    build_directory: .batect/my-container
    dockerfile: my-custom-Dockerfile
```

## `enable_init_process`
<small>**Equivalent Docker CLI option**: `--init` to `docker run`, **equivalent Docker Compose option**: `init`</small>

Set to `true` to pass the [`--init`](https://docs.docker.com/engine/reference/run/#specify-an-init-process) flag when running the container.
Defaults to `false`.

This creates the container with a simple PID 1 process to handle the responsibilities of the init system, which is required for some applications to behave correctly.

[Read this article](https://engineeringblog.yelp.com/2016/01/dumb-init-an-init-for-docker.html) to understand more about the behaviour
of different processes running as PID 1 and why this flag was introduced.

For example, running the container `build-env` from the following configuration will launch a container that uses the `node:10.10.0-alpine` image with Docker's default init process as PID 1:

```yaml
containers:
  build-env:
    image: node:10.10.0-alpine
    enable_init_process: true
```

## `entrypoint`
<small>**Equivalent Docker CLI option**: `--entrypoint` to `docker run`, **equivalent Docker Compose option**: `entrypoint`</small>

Entrypoint to use to run [command](#command) or the image's default command if `command` is not provided.

If not provided, the default entrypoint for the image will be used.

Both of these can be overridden for an individual task by specifying an [`entrypoint` at the task level](Tasks.md#entrypoint).

See the Docker docs for [`CMD`](https://docs.docker.com/engine/reference/builder/#cmd) and
[`ENTRYPOINT`](https://docs.docker.com/engine/reference/builder/#entrypoint) for more information on how the entrypoint is used.
batect will always convert the entrypoint provided here to the exec form when passed to Docker.

For example, the container `my-container` from the following configuration will use `sh -c` as its entrypoint and ignore the default entrypoint set in `my-image`:

```yaml
containers:
  my-container:
    image: my-image
    entrypoint: sh -c
```

## `environment`
<small>**Equivalent Docker CLI option**: `--env` to `docker run`, **equivalent Docker Compose option**: `environment`</small>

List of environment variables (in `name: value` format) for the container.

Values can be [expressions](Overview.md#expressions).

### Example
Let's assume we have the following configuration:

```yaml
containers:
  build-env:
    image: ruby:2.4.3
    environment:
      COUNTRY: Australia
      SUPER_SECRET_VALUE: $SECRET_PASSWORD
      OPTIMISATION_LEVEL: ${HOST_OPTIMISATION_LEVEL:-none}
```

Running the container `build-env` will launch a container that uses the `ruby:2.4.3` image with the following environment variables:

* The environment variable `COUNTRY` will have value `Australia`.

* The environment variable `SUPER_SECRET_VALUE` will have the value of the `SECRET_PASSWORD` environment variable on
  the host. (So, for example, if `SECRET_PASSWORD` is `abc123` on the host, then `SUPER_SECRET_VALUE` will have the value `abc123` in the container.)

    If `SECRET_PASSWORD` is not set on the host, batect will show an error message and not start the task.

* The environment variable `OPTIMISATION_LEVEL` will have the value of the `HOST_OPTIMISATION_LEVEL` environment variable on the host.

    If `HOST_OPTIMISATION_LEVEL` is not set on the host, then `OPTIMISATION_LEVEL` will have the value `none` in the container.

### `TERM`
The `TERM` environment variable, if set on the host, is always automatically passed through to the container. This ensures that features such as
coloured output continue to work correctly inside the container.

### Proxy-related environment variables
Proxy-related environment variables, if set on the host, are passed through to the container at build and run time, but are not used for image pulls.

If a proxy-related environment variable is defined on the container's configuration, it takes precedence over the host-provided value.

See [this page](../tips/Proxies.md) for more information on using batect with proxies.

## `health_check`
<small>**Equivalent Docker CLI option**: `--health-cmd`, `--health-interval`, `--health-retries` and `--health-start-period` to `docker run`, **equivalent Docker Compose option**: `healthcheck`</small>

Overrides the [health check configuration](https://docs.docker.com/engine/reference/builder/#healthcheck) specified in the image:

### `command`
The command to run to check the health of the container.

If this command exits with code 0, the container is considered healthy, otherwise the container is considered unhealthy.

### `retries`
The number of times to perform the health check before considering the container unhealthy.

### `interval`
The interval between runs of the health check.

Accepts values such as `2s` (two seconds) or `1m` (one minute).

### `start_period`
The time to wait before failing health checks count against the retry count. During this period, the health check still runs,
and if the check succeeds during this time, the container is immediately considered healthy.

Accepts values such as `2s` (two seconds) or `1m` (one minute).

### Example

The following configuration uses a fictional `is-healthy` command every two seconds to determine if the container is healthy.
After an initial three second waiting period, the container will be declared unhealthy if it fails the health check five more times.

```yaml
containers:
  my-container:
    health_check:
      command: is-healthy localhost:8080
      interval: 2s
      retries: 5
      start_period: 3s
```

## `image`
<small>**Equivalent Docker CLI option**: argument to `docker run`, **equivalent Docker Compose option**: `image`</small>

Image name (in standard Docker image reference format) to use for this container. **One of `image` or `build_directory` is required.**

If the image has not already been pulled, batect will pull it before starting the container.

The image can be overridden from the command line when running a task with [`--override-image`](../CLIReference.md#override-the-image-used-by-a-container-override-image).

For example, the container `my-container` from the following configuration will use the `ruby:2.4.3` image:

```yaml
containers:
  my-container:
    image: ruby:2.4.3
```

!!! tip
    It is highly recommended that you specify a specific image version, and not use `latest`, to ensure that the same image is used
    everywhere. For example, use `alpine:3.7`, not `alpine` or `alpine:latest`.

## `log_driver`
<small>**Equivalent Docker CLI option**: `--log-driver` to `docker run`, **equivalent Docker Compose option**: `logging.driver`</small>

The Docker log driver to use when running the container.

Defaults to `json-file` if not set.

A full list of built-in log drivers is available in [the logging section of Docker documentation](https://docs.docker.com/config/containers/logging/configure/#supported-logging-drivers),
and [logging plugins](https://docs.docker.com/config/containers/logging/plugins/) can be used as well.

Options for the log driver can be provided with [`log_options`](#log_options).

For example, the container `my-container` from the following configuration will use the `syslog` log driver:

```yaml
containers:
  my-container:
    log_driver: syslog
```

!!! warning
    Some log drivers do not support streaming container output to the console, as described in
    [the limitations section of Docker's logging documentation](https://docs.docker.com/config/containers/logging/configure/#limitations-of-logging-drivers).

    If the selected log driver does not support streaming container output to the console, you will see error messages similar to
    `Error attaching: configured logging driver does not support reading` in batect's output. This does not affect the execution of the task, which
    will run to completion as per normal.

## `log_options`
<small>**Equivalent Docker CLI option**: `--log-opt` to `docker run`, **equivalent Docker Compose option**: `logging.options`</small>

Options to provide to the Docker log driver used when running the container.

For example, to set [the tag used to identify the container in logs](https://docs.docker.com/config/containers/logging/log_tags/):

```yaml
containers:
  my-container:
    log_options:
      tag: "my-container"
```

The options available for each log driver are described in the Docker documentation for that log driver, such as
[this page](https://docs.docker.com/config/containers/logging/json-file/) for the `json-file` driver.

## `ports`
<small>**Equivalent Docker CLI option**: `--publish` to `docker run`, **equivalent Docker Compose option**: `ports`</small>

List of ports to make available to the host machine.

Three formats are supported:

* `local:container` or `local:container/protocol` format

    For example, `1234:5678` or `1234:5678/tcp` will make TCP port 5678 inside the container available on the host machine at TCP port 1234, and
    `1234:5678/udp` will make UDP port 5678 inside the container available on the host machine at UDP port 1234.

* `local_from-local_to:container_from:container-to` or `local_from-local_to:container_from:container-to/protocol` format

    For example, `1000-1001:2025-2026` or `1000-1001:2025-2026/tcp` will make TCP port 2025 inside the container available
    on the host machine at TCP port 1000, and TCP port 2026 inside the container available on the host machine at TCP port 1001.

* An expanded format:
  ```yaml
  containers:
    my-container:
      ...
      ports:
        # This is equivalent to 1234:5678 or 1234:5678/tcp
        - local: 1234
          container: 5678
        # This is equivalent to 3000:4000/udp
        - local: 3000
          container: 4000
          protocol: udp
        # This is equivalent to 1000-1001:2025-2026 or 1000-1001:2025-2026/tcp
        - local: 1000-1001
          container: 2025-2026
        # This is equivalent to 5000-5001:6025-6026/udp
        - local: 5000-5001
          container: 6025-6026
          protocol: udp
  ```

All protocols supported by Docker are supported. The default protocol is TCP if none is provided.

The port does not need to have a corresponding `EXPOSE` instruction in the Dockerfile.

For example, the `my-container` container in the following configuration allows accessing port 5678 from the container on port 1234 on the host machine:

```yaml
container:
  my-container:
    ports:
      - 1234:5678
      # or
      - local: 1234
        container: 5678
```

!!! tip
    Exposing ports is only required if you need to access the container from the host machine.

    Any container started as part of a task will be able to access any port on any other container at the address `container_name:container_port`, even if that port
    is not listed in `ports`.

    For example, if a process running in the `http-server` container listens on port 2000, any other container in the task can access that at `http-server:2000`
    without port 2000 being listed in `ports` (or an `EXPOSE` Dockerfile instruction).

## `privileged`
<small>**Equivalent Docker CLI option**: `--privileged` to `docker run`, **equivalent Docker Compose option**: `privileged`</small>

Set to `true` to run the container in [privileged mode](https://docs.docker.com/engine/reference/commandline/run/#full-container-capabilities---privileged). Defaults to `false`.

See also [`capabilities_to_add` and `capabilities_to_drop`](#capabilities_to_add-and-capabilities_to_drop).

For example, the following configuration runs the `my-container` container in privileged mode:

```yaml
containers:
  my-container:
    privileged: true
```

## `run_as_current_user`
<small>**Equivalent Docker CLI option**: none, **equivalent Docker Compose option**: none</small>

Run the container with the same UID and GID as the user running batect (rather than the user the Docker daemon runs as, which is root
on Linux). This means that any files created by the container will be owned by the user running batect, rather than root.

This is really only useful on Linux. On macOS, the Docker daemon runs as the currently logged-in user and so any files created in the container are owned
by that user, so this is less of an issue. However, for consistency, the same configuration changes are made on both Linux and macOS.

See [this page](../tips/BuildArtifactsOwnedByRoot.md) for more information on the effects of this option and why it is necessary.

`run_as_current_user` has the following options:

### `enabled`
Set to `true` to enable 'run as current user' mode. Defaults to `false`.

### `home_directory`
Directory to use as home directory for user inside container.

Required if `enabled` is `true`, not allowed if `enabled` is `false`.

This directory is automatically created by batect with the correct owner and group.

!!! warning
    If the directory given by `home_directory` already exists inside the image for this container, it is overwritten.

### Example

```yaml
containers:
  my-container:
    image: ruby:2.4.3
    run_as_current_user:
      enabled: true
      home_directory: /home/container-user
```

## `setup_commands`
<small>**Equivalent Docker CLI option**: none, **equivalent Docker Compose option**: none</small>

List of commands to run inside the container after it has become healthy but before dependent containers start.

See [the task lifecycle](../TaskLifecycle.md) for more information on the effects of this option.

!!! tip
    It is recommended that you try to include any setup work in your image's Dockerfile wherever possible (and not use setup commands). Setup commands must be
    run every time the container starts whereas commands included in your image's Dockerfile only run when the image needs to be built, reducing the time taken
    for tasks to start.

Each setup command has the following options:

### `command`
The command to run. **Required.**

This command is run in a similar way to the container's [`command`](#command), so the same limitations apply to using shell syntax such as `&&`.

### `working_directory`

The working directory to use for the command.

If no working directory is provided, [`working_directory`](#working_directory) is used if it is set, otherwise the image's default working directory is used.
If this container is used as the task container and the task overrides the default working directory, that override is ignored when running setup commands.

The command will inherit the same environment variables as the container's `command` (including any specified on the task if this is the task container), runs as the
same [user and group](#run_as_current_user) as the container's `command` and inherits the same settings for [privileged status](#privileged) and
[capabilities](#capabilities_to_add-and-capabilities_to_drop).

### Example

Let's assume we have the following configuration:

```yaml
containers:
  database:
    setup_commands:
      - command: ./apply-migrations.sh

  application:
    dependencies:
      - database
```

Running the container `application` will first build or pull the images for both the `database` and `application` containers.

Once the image for `database` is ready, `database` will start and launch the command specified in the Dockerfile, then batect will wait for the container to report as healthy.
Once `database` reports as healthy, it will run `./apply-migrations.sh` and wait for it to finish before then starting `application`.

## `volumes`
<small>**Equivalent Docker CLI option**: `--volume` to `docker run`, **equivalent Docker Compose option**: `volumes`</small>

List of volume mounts to create for the container.

Both local mounts (mounting a directory on the host into a container) and [cache mounts](../tips/Performance.md#cache-volumes) are supported:

### Local mounts

Two formats are supported:

* `local:container` or `local:container:options` format

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

In both formats, the following fields are supported:

* `local`: path to the local file or directory to mount. Can be an [expression](Overview.md#expressions) when using the expanded format. Required.

    Relative paths will be resolved relative to the current configuration file's directory.

    On Windows, the local path can use either Windows-style (`path\to\thing`) or Unix-style (`path/to/thing`) paths, but for compatibility
    with users running on other operating systems, using Unix-style paths is recommended.

* `container`: path to mount the local file or directory at inside the container. Required.
* `options`: standard Docker mount options (such as `ro` for read-only). Optional.

Using `options: cached` may improve performance when running on macOS and Windows hosts - see [this page](../tips/Performance.md#io-performance) for further explanation.

### Cache mounts

Cache mounts provide persistence between task runs without the performance overhead of mounting a directory from the host into the container.

They are perfect for directories such as `node_modules` which contain downloaded dependencies that can safely be reused for each task run.

The format for a cache mount is:

```yaml
containers:
  my-container:
    ...
    volumes:
      - type: cache
        name: node-modules
        container: /code/node_modules
```

The following fields are supported:

* `type`: must be set to `cache`. Required.
* `name`: name of the cache, must be a valid Docker volume name. The same name can be used to share a cache between multiple containers. Required.
* `container`: path to mount the cache directory at inside the container. Required.
* `options`: standard Docker mount options (such as `ro` for read-only). Optional.

## `working_directory`
<small>**Equivalent Docker CLI option**: `--workdir` to `docker run`, **equivalent Docker Compose option**: `working_dir`</small>

Working directory to start the container in.

If not provided, the default working directory for the image will be used.

Both of these can be overridden for an individual task by specifying a [`working_directory` at the task level](Tasks.md#run).

For example, the container `my-container` in the following configuration will start with the working directory set to `/somewhere`:

```yaml
containers:
  my-container:
    working_directory: /somewhere
```
