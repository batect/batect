# Performance

## I/O performance

!!! tip "tl;dr"
    If you're seeing slow build times under batect on OS X, volume mount options such as `cached` might help

!!! info
    This section only applies to OS X-based hosts, and is only supported by Docker version 17.04 and higher.

Docker requires features only found in the Linux kernel, and so on OS X, Docker for Mac runs a lightweight virtual machine
to host Docker. However, while this works perfectly fine for most situations, there is some overhead involved in operations
that need to work across the host / virtual machine boundary.

Usually this overhead is so small that it's not noticeable, but for operations involving mounted volumes, this overhead can
be significant. In particular, this can impact compilation operations involving reading and writing many files.

There is a way to reduce this overhead significantly: use the
[volume mount options introduced in Docker 17.04](https://docs.docker.com/docker-for-mac/osxfs-caching/).

In particular, for the typical scenario where you are editing code files on your host's disk and mounting that into a container
for compilation, using the `cached` volume mount mode can result in a significant performance improvement - we saw an improvement
in compilation times of ~60% on one Golang project once we started using this.

(Before you use these options in another context, you should consult the
[documentation](https://docs.docker.com/docker-for-mac/osxfs-caching/) to understand the implications of the option.)

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
OS X and others use Linux or Windows.

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
