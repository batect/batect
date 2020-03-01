# CLI reference

!!! note
    This page reflects the options available in the [most recent version](https://github.com/batect/batect/releases/latest)
    of batect.

    If you are not running the most recent version, run `./batect --help` to see what options are available in your version.

## Run a task

Run a task by running `./batect <task-name>`. For example, to run `the-task`, run `./batect the-task`.

You can also pass arguments to the task command by passing those arguments by running `./batect <task-name> -- <args...>`.
For example, to run the task `the-task` with the arguments `arg1 arg2`, run `./batect the-task -- arg1 arg2`.

### Set config variables from a file <small>(`--config-vars-file`)</small>

By default, batect will automatically apply values for [config variables](config/ConfigVariables.md) from the YAML file `batect.local.yml`
if it exists.

Use `--config-vars-file` to specify a different file to use.

Values provided with [`--config-var`](#set-a-config-variable-config-var) take precedence over values provided in any file.

Example:

```shell
./batect --config-vars-file batect.ci.yml the-task
```

Example `batect.ci.yml` contents:

```yaml
log_level: debug
user_name: alex
```

### Set a config variable <small>(`--config-var`)</small>

Use `--config-vars` to specify values for an individual [config variable](config/ConfigVariables.md).

Values must be in the format `<variable name>=<variable value>`.

Values provided with `--config-var` take precedence over values provided in a file (either explicitly with
[`--config-vars-file`](#set-config-variables-from-a-file-config-vars-file) or from the default `batect.local.yml` file)
and default values defined in the configuration file.

Example:

```shell
./batect --config-var log_level=debug the-task
```

### Override the image used by a container <small>(`--override-image`)</small>

By default, batect will use the image defined in the configuration file (either with [`image`](config/Containers.md#image) or [`build_directory`](config/Containers.md#build_directory)).

Use this option to override the value in the configuration file and use a different image for a specific container.

Values must be in the format `<container name>=<image>`.

Example:

```shell
./batect --override-image build-env=ruby:2.7.0 the-task
```

### Disable cleaning up <small>(`--no-cleanup`, `--no-cleanup-after-failure` and `--no-cleanup-after-success`)</small>

By default, batect will automatically cleanup all containers and other resources it creates while running a task.
However, sometimes it can be useful to leave all the created containers running to diagnose issues running a task.

* Use `--no-cleanup-after-failure` to not clean up if any task fails to start for any reason.
* Use `--no-cleanup-after-success` to not clean up the containers and other resources created for the main task (the one specified on the command line) if it succeeds.
* Use `--no-cleanup` to enable both of the above.

Example:

```shell
./batect --no-cleanup-after-failure the-task
```

### Disable propagation of proxy-related environment variables <small>(`--no-proxy-vars`)</small>

By default, batect will automatically propagate proxy-related environment variables as described [here](tips/Proxies.md).
Use this option to disable this behaviour.

Example:

```shell
./batect --no-proxy-vars the-task
```

## See a list of available tasks <small>(`--list-tasks` or `-T`)</small>

batect can produce a short summary of all tasks in the current configuration file along with their
[`description`](config/Tasks.md#description), and grouped by their [`group`](config/Tasks.md#group).

For example, `./batect --list-tasks` produces output like this:

```
Build tasks:
- build: Build the application.

Test tasks:
- continuousUnitTest: Run the unit tests in watch mode.
- unitTest: Run the unit tests once.

Utility tasks:
- outdated: Check for outdated dependencies.
- shell: Start a shell in the development environment.
```

## Upgrade batect <small>(`--upgrade`)</small>

Running `./batect --upgrade` will automatically upgrade batect in the current project to the latest
available version.

## Get help for batect's CLI <small>(`--help`)</small>

Running `./batect --help` will show a summary of the options available on the command line.

## Get batect, Docker and OS version information <small>(`--version`)</small>

Running `./batect --version` will show a summary of the versions of batect, Docker and your operating
system, which can be useful when diagnosing issues with batect.

## Common options

### Use a non-standard configuration file name <small>(`--config-file` or `-f`)</small>

By default, batect will use a configuration file called `batect.yml` in the current directory.
Use this option to instruct batect to use a different file.

Example:

```shell
./batect --config-file my-other-config-file.yml the-task
```

### Customise cache storage mechanism <small>(`--cache-type`)</small>

By default, batect will use a Docker volume for each [cache mount](tips/Performance.md#cache-volumes).
Use this option to instruct batect to use a different storage mechanism.

Supported values are:

* `volume`: use Docker volumes
* `directory`: use directories mounted from the project's `.batect/caches` directory

The `BATECT_CACHE_TYPE` environment variable can also be used to set the default cache type. If both the environment
variable and the `--cache-type` option are set, the value given with `--cache-type` takes precedence. 

Example:

```shell
./batect --cache-type=directory the-task
```

### Customise Docker connection options

#### Use a non-standard Docker host <small>(`--docker-host`)</small>

By default, batect will connect to the Docker daemon using the path provided in the `DOCKER_HOST` environment variable, or
the default path for your operating system if `DOCKER_HOST` is not set. Use this option to instruct batect to use a different path.

Example:

```shell
./batect --docker-host unix:///var/run/other-docker.sock the-task
```

#### Connect to Docker over TLS <small>(`--docker-tls` and `--docker-tls-verify`)</small>

By default, the Docker daemon only accepts plaintext connections from the local machine. If your daemon requires TLS, use the `--docker-tls-verify` option
to instruct batect to use TLS. batect will also automatically enable this option if the `DOCKER_TLS_VERIFY` environment variable is set to `1`.

If your daemon presents a certificate that does not match its hostname, use the `--docker-tls` option (without `--docker-tls-verify`) to instruct
batect to not verify the hostname.

!!! warning
    Using `--docker-tls` without `--docker-tls-verify` is insecure and should only be used if you understand the implications of this.

These options mirror the behaviour of the `docker` CLI's `--tls` and `--tlsverify` options.

#### Customise certificates used to provide authentication to daemon and to verify daemon's identity <small>(`--docker-cert-path`, `--docker-tls-ca-cert`, `--docker-tls-cert` and `--docker-tls-key`)</small>

If your Docker daemon requires TLS, batect needs three files in order to connect to it:

* the CA certificate that can be used to verify certificates presented by the Docker daemon (`--docker-tls-ca-cert`)
* the certificate that can be used to prove your identity to the Docker daemon (`--docker-tls-cert`) and corresponding private key (`--docker-tls-key`)

By default, these files are stored in `~/.docker` and are named `ca.pem`, `cert.pem` and `key.pem` respectively.

You can instruct batect use a non-default location for any of these files with the options mentioned above, or override the default directory for these
files with `--docker-cert-path`. If the `DOCKER_CERT_PATH` environment variable is set, batect will use that as the default directory.

If both `--docker-cert-path` (or `DOCKER_CERT_PATH`) and a path for an individual file is provided, the path for the individual file takes precedence.

These options mirror the behaviour of the `docker` CLI's `--tlscacert`, `--tlscert` and `--tlskey` options.

### Create a debugging log <small>(`--log-file`)</small>

Use this option to instruct batect to generate a debugging log at the specified path as it runs. This may be requested if you submit an issue.

If the log file already exists, batect will append further log messages to the end of the file.

Example:

```shell
./batect --log-file /tmp/debugging-log.json the-task
```

### Disable coloured output <small>(`--no-color`)</small>

By default, batect will produce coloured output if it detects that your console supports it. However, sometimes batect may incorrectly
believe your console supports coloured output, or your console may incorrectly report that it supports coloured output when it does not.
(This is a common issue with some CI systems.) This can lead to garbled or difficult to read output.

Passing this flag will disable all coloured output, even if batect believes your console supports it.

Example:

```shell
./batect --no-color the-task
```

### Disable update notification <small>(`--no-update-notification`)</small>

batect automatically checks for updates at most once every 24 hours and displays a notification if a newer version is available.

Passing this flag will disable both the update check and notification.

### Force a particular output style <small>(`--output` or `-o`)</small>

batect offers four styles of output:

* `fancy` is best for interactive use, providing very clean output about the current state of execution and showing output from only the task container

    <asciinema-player src="/assets/outputstyles/fancy.cast" cols="204" rows="20" preload="true" poster="npt:2.5"></asciinema-player>

* `simple` is best for non-interactive use (eg. on CI), providing a log of what happened and showing output from only the task container

    <asciinema-player src="/assets/outputstyles/simple.cast" cols="204" rows="20" preload="true" poster="npt:2.5"></asciinema-player>

* `all` displays output from all containers

    <asciinema-player src="/assets/outputstyles/interleaved.cast" cols="204" rows="20" preload="true" poster="npt:4"></asciinema-player>

* `quiet` displays only the output from the task and error messages from batect

    <asciinema-player src="/assets/outputstyles/quiet.cast" cols="204" rows="20" preload="true" poster="npt:7"></asciinema-player>

By default, batect will automatically pick an output style that it believes is appropriate for the environment it is running in -
`fancy` if it believes your environment supports it, or `simple` otherwise.

Passing this flag allows you to override what batect believes is appropriate.

Example:

```shell
./batect --output simple the-task
```

## General notes

* All command line options that take a value can be provided in `--option=value` or `--option value` format.
