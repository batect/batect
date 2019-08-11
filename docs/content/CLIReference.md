# CLI reference

!!! note
    This page reflects the options available in the most recent version of batect.

    If you are not running the most recent version, run `./batect --help` to see what options are available in your version.

## Run a task

Run a task by running `./batect <task-name>`. For example, to run `the-task`, run `./batect the-task`.

You can also pass arguments to the task command by passing those arguments by running `./batect <task-name> -- <args...>`.
For example, to run the task `the-task` with the arguments `arg1 arg2`, run `./batect the-task -- arg1 arg2`.

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

### Override the default level of parallelism <small>(`--level-of-parallelism` or `-p`)</small>

batect runs whatever operations it can in parallel to make tasks run as fast as possible. It automatically detects an appropriate
number of operations to run in parallel based on your system. Use this option to override this default value.

Example:

```shell
./batect --level-of-parallelism 10 the-task
```

### Disable propagation of proxy-related environment variables <small>(`--no-proxy-vars`)</small>

By default, batect will automatically propagate proxy-related environment variables as described [here](tips/Proxies.md).
Use this option to disable this behaviour.

Example:

```shell
./batect --no-proxy-vars the-task
```

## See a list of available tasks <small>(`--list-tasks`)</small>

batect can produce a short summary of all tasks in the current configuration file along with their `description`, and
grouped by their `group`.

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

### Use a non-standard Docker host <small>(`--docker-host`)</small>

By default, batect will connect to the Docker daemon using the path provided in the `DOCKER_HOST` environment variable, or
the default path for your operating system if `DOCKER_HOST` is not set. Use this option to instruct batect to use a different path.

Example:

```shell
./batect --docker-host unix:///var/run/other-docker.sock the-task
```

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

batect offers three styles of output:

* `fancy` is best for interactive use, providing very clean output about the current state of execution
* `simple` is best for non-interactive use (eg. on CI), providing a log of what happened
* `quiet` displays only the output from the task and error messages from batect

By default, batect will automatically pick an output style that it believes is appropriate for the environment it is running in -
`fancy` if it believes your environment supports it, or `simple` otherwise.

Passing this flag allows you to override what batect believes is appropriate.

Example:

```shell
./batect --output simple the-task
```

## General notes

* All command line options that take a value can be provided in `--option=value` or `--option value` format.
