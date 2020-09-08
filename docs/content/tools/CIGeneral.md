# General setup for CI systems

## Requirements

batect can be used with any CI system that can execute arbitrary commands.

CI agents must meet batect's normal [requirements](../index.md#what-are-batects-system-requirements).

## Long-lived agents

!!! tip "tl;dr"
    Set up a Cron job to run `docker image prune -f` regularly on long-lived CI agents

If you are using Dockerfiles to define your containers (as opposed to using a pre-existing image), this can generate a
large number of orphaned images (and their associated image layers) over time. While batect goes to great lengths to
ensure that containers and networks are cleaned up after every task run, it can't know which images are unused and so
it can't safely automatically remove unused images.

These orphaned images take up disk space, and, if left unattended, can lead to exhausting all the available disk space.
This is especially a problem on CI agents, where a human might not notice this issue until the disk is full.

Therefore, it's recommended that CI agents running batect-based builds have a regular task that removes orphaned images.
Docker has a built-in command to do this: `docker image prune -f` (the `-f` disables the confirmation prompt). The exact
frequency will depend on your usage pattern, but once a day is usually more than sufficient.

## Port conflicts

!!! tip "tl;dr"
    Disable binding of ports on the host system by running tasks with the
    [`--disable-ports`](../CLIReference.md#disable-port-binding-on-the-host-machine-disable-ports) flag

If a single host machine can run multiple build jobs at the same time, this can result in port conflicts if multiple jobs
run tasks that attempt to bind to the same port.

Normally, on CI, bound ports aren't used, so disabling them has no effect and prevents any issues caused by port
conflicts.

To disable port bindings, run the task with `--disable-ports`. For example, run `the-task` with `./batect --disable-ports the-task`.
