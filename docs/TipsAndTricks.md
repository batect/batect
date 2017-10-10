# Tips and tricks

## CI setup

If you are using Dockerfiles to define your containers (as opposed to taking a pre-existing image), this can generate a
large number of orphaned images (and their associated image layers) over time. While batect goes to great lengths to
ensure that containers and networks are cleaned up after every task run, it can't know which images are unused and so
remove them.

These orphaned images take up disk space, and, if left unattended, can lead to exhausting all the available disk space.
This is especially a problem on CI, where a human might not notice this issue until too late.

Therefore, it's recommended that CI servers running batect-based builds have a regular task that removes orphaned images.
Docker has a built-in command to do this: `docker image prune -f` (the `-f` disables the confirmation prompt). The exact
frequency will depend on your usage pattern.

## IDE integration

Many IDEs rely on having the development environment installed locally in order to provide features like code completion,
analysis and tool integration. (For example, a Ruby IDE might need access to a Ruby runtime, and a Java IDE might need
the target JVM to be installed.) However, if you're using batect, then all of this is in a container and so the IDE can't
access it.

Some solutions for this include:

* Some of the JetBrains family of products natively supports using a SDK or runtime from a container (PyCharm and RubyMine
  are known to work, although notably IntelliJ does not currently support this). There's more information on how to configure
  this in the [PyCharm docs](https://www.jetbrains.com/help/pycharm/configuring-remote-interpreters-via-docker.html) and
  [RubyMine docs](https://www.jetbrains.com/help/ruby/configuring-remote-interpreters-via-docker.html).
* You could run a text-based editor such as Vim or Emacs in a container (managed by batect, of course) that has your
  required runtime components installed alongside it.

(Have you tried something else that worked? Or do you use another IDE or text editor that supports using runtimes inside a
container? Please [submit a PR](https://github.com/charleskorn/batect/pulls) to add to the list above.)

## Performance

### I/O performance

_This section only applies to OS X-based hosts._

Docker requires features only found in the Linux kernel, and so on OS X, Docker for Mac runs a lightweight virtual machine
to host Docker. However, while this works perfectly fine for most situations, there is some overhead involved in operations
that need to work across the host / virtual machine boundary. Usually this overhead is so small that it's not noticeable, but
for operations involving mounted volumes, this overhead can be significant. In particular, this can impact compilation
operations involving reading and writing many files.

There is a way to reduce this overhead significantly: use of Docker's
[volume mount options introduced in Docker 17.04](https://docs.docker.com/docker-for-mac/osxfs-caching/). In particular,
for the typical scenario where you are editing code files on your host's disk and mounting that into a container for compilation,
using the `:cached` volume mount mode can result in a significant performance improvement - we saw an improvement in compilation
times of ~60% on one Golang project once we started using this.

Important: before you use these options in another context, you should consult the
[documentation](https://docs.docker.com/docker-for-mac/osxfs-caching/) to understand the implications of the option.

### Database schema and test data

A significant amount of time during integration or journey testing with a database can be taken up by preparing the database for
use - setting up the schema (usually with some kind of migrations system) and adding the initial test data can take quite some time,
especially as the application evolves over time. One way to address this is to bake the schema and test data into the Docker image
used for the database, so that this setup cost only has to be paid when building the image or when the setup changes, rather than on
every test run. The exact method for doing this will vary depending on the database system you're using, but the general steps that would
go in your Dockerfile are:

1. Copy schema and test data scripts into container
2. Temporarily start database daemon and run schema and data scripts against database instance, then shut down database daemon
