# Performance

## I/O performance

!!! tip "tl;dr"
    If you're seeing slow build times under batect on macOS or Windows, using batect's caches as well as volume mount options such as `cached` might help

Docker requires features only found in the Linux kernel, and so on macOS and Windows, Docker Desktop runs a lightweight Linux virtual machine
to host Docker. However, while this works perfectly fine for most situations, there is some overhead involved in operations
that need to work across the host / virtual machine boundary, particularly when it comes to mounting files or directories into a container from the host.

While the throughput of mounts on macOS and Windows is generally comparable to native file access within a container, the latency
performing I/O operations such as opening a file handle can often be significant, as these need to cross from the Linux VM hosting Docker to the
host OS and back again.

This increased latency quickly accumulates, especially when many file operations are involved. This particularly affects languages such as JavaScript
and Golang that encourage distributing all dependencies as source code and breaking codebases into many small files, as even a warm build with no source
code changes still requires the compiler to examine each dependency file to ensure that the cached build result is up-to-date.

There are two ways to improve the performance of file I/O when using batect:

* Use [a batect cache backed by a Docker volume](#cache-volumes) wherever possible
* Otherwise, use [the `cached` mount mode](#mounts-in-cached-mode)

### Cache volumes

The performance penalty of mounting a file or directory from the host machine does not apply to [Docker volumes](https://docs.docker.com/storage/volumes/),
as these remain entirely on the Linux VM hosting Docker. This makes them perfect for directories such as caches where persistence between task runs is required,
but easy access to their contents is not necessary.

batect makes this simple to configure. In your container definition, add a mount to [`volumes`](../config/Containers.md#volumes) with `type: cache`.

For example, for a typical Node.js application, to cache the `node_modules` directory in a volume, include the following in your configuration:

```yaml
containers:
  build-env:
    image: "node:13.8.0"
    volumes:
      - local: .
        container: /code
      - type: cache
        name: app-node-modules
        container: /code/node_modules
    working_directory: /code
```

!!! tip
    To make it easier to share caches between builds on ephemeral CI agents, you can use directories mounted from the project's `.batect/caches` directory
    instead of volumes and then archive this directory between builds. Run batect with `--cache-type=directory` to enable this behaviour.

    Using mounted directories instead of volumes has no performance impact on Linux.

### Mounts in `cached` mode

!!! info
    This section only applies to macOS-based hosts, and is only supported by Docker version 17.04 and higher.
    Enabling `cached` mode is harmless for other host operating systems.

For situations where a cache is not appropriate (eg. mounting your code from the host into a build environment), specifying the `cached` volume mount option
can result in significant performance improvements.

(Before you use this option in another context, you should consult the [documentation](https://docs.docker.com/docker-for-mac/osxfs-caching/) to understand the implications of it.)

For example, instead of defining your container like this:

```yaml
containers:
  build-env:
    image: "ruby:2.4.3"
    volumes:
      - local: .
        container: /code
    working_directory: /code
```

use this:

```yaml
containers:
  build-env:
    image: "ruby:2.4.3"
    volumes:
      - local: .
        container: /code
        options: cached # This enables 'cached' mode for the /code mount
    working_directory: /code
```

Setting this option will not affect Linux or Windows hosts, so it's safe to commit and share this in a project where some developers use
macOS and others use Linux or Windows.

## Database schema and test data

!!! tip "tl;dr"
    Try to do as much work as possible at image build time, rather than doing it every time the container starts

A significant amount of time during integration or journey testing with a database can be taken up by preparing the database for
use - setting up the schema (usually with some kind of migrations system) and adding the initial test data can take quite some time,
especially as the application evolves over time.

One way to address this is to bake the schema and test data into the Docker image used for the database, so that this setup cost only
has to be paid when building the image or when the setup changes, rather than on every test run. The exact method for doing this will
vary depending on the database system you're using, but the general steps that would go in your Dockerfile are:

1. Copy schema and test data scripts into container
2. Temporarily start database daemon
3. Run schema and data scripts against database instance
4. Shut down database daemon

## Shutdown time

!!! tip "tl;dr"
    Make sure signals such as SIGTERM and SIGKILL are being passed to the main process

If you notice that post-task cleanup for a container is taking longer than expected, and that container starts the main process from a
shell script, make sure that signals such as SIGTERM and SIGKILL are being forwarded to the process. (Otherwise Docker will wait 10
seconds for the application to respond to the signal before just terminating the process.)

For example, instead of using:

```bash
#! /usr/bin/env bash

/app/my-really-cool-app --do-stuff
```

use this:

```bash
#! /usr/bin/env bash

exec /app/my-really-cool-app --do-stuff
```

([source](https://unix.stackexchange.com/a/196053/258093))
