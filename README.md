# batect
[![Build Status](https://travis-ci.org/charleskorn/batect.svg?branch=master)](https://travis-ci.org/charleskorn/batect)

**b**uild **a**nd **t**esting **e**nvironments as **c**ode **t**ool

## The sales pitch

* Consistent, fast, repeatable, isolated builds and test runs everywhere: your computer, your colleagues' computers and on CI
* Manage dependencies for integration and end-to-end testing with ease
* No installation required, only dependencies are Bash, Docker and `curl`*
* Works with your existing CI system, and your chosen language's existing tooling
* Take advantage of existing Docker images to get started quickly

\* at the moment, a JVM is also required, but this requirement will be removed before v1.0

[![asciicast](https://asciinema.org/a/vBV5iQz4jqyhKPEfkiJyM4kEt.png)](https://asciinema.org/a/vBV5iQz4jqyhKPEfkiJyM4kEt)

## Documentation

* [Getting started guide](docs/GettingStarted.md)
* [Configuration file reference](docs/ConfigFile.md)
* [Tips and tricks](docs/TipsAndTricks.md)

There are also [Java](https://github.com/charleskorn/batect-sample-java) and [Ruby](https://github.com/charleskorn/batect-sample-ruby)
sample projects you can use as a basis for your application.

## Motivation - why would I use this?

Every application has a build environment - the tools and configuration needed to take the source code and produce an artifact
ready for use. However, setting this up can be time consuming and frustrating. Too often new team members' first experience on
a project is a few days working out which tools they need to install, and another few days of then discovering the magic
combination of tool versions that will happily coexist. And as the application evolves and changes over time, maintaining and
updating this environment across all developers' machines and CI agents can be incredibly painful.

Similarly, most applications have external dependencies - for example, other services, databases, caches, credential storage
systems... the list is endless. Because of this, we would like to run integration, component or journey tests where the
application itself (or some part of it) interacts with these external dependencies. In some cases, we'd like to use a real
instance of it (eg. a running Postgres instance), and in other cases, we'd like to use a fake (eg. a fake implementation of a
downstream service). Either way, installing, configuring and managing all these dependencies takes a lot of work, and making
sure they're in a known state before a test run is key to reducing test flakiness. Add in networking gremlins, different
operating systems, personal preferences, built-up cruft and manual configuration and you end up with an enormous number of
variables that lead to a huge amount of wasted time spent debugging issues that are entirely preventable.

batect helps solve these problems by:

* allowing you to entirely automate the setup of your build and testing environments
* storing this automation alongside your application code, so that it is versioned and updated just like any other part of
  your application
* ensuring that every single command invocation starts with a completely fresh environment based on your configuration file,
  making it impossible to get out-of-sync from the desired state
* making use of Docker to do all of this in an isolated and low-overhead way
* using some smart dependency management logic, parallelism and Docker's caching features to do all of this very, very quickly
* taking advantage of Docker's networking features to set up an isolated network for every command
* enabling you to use existing Docker images as-is (or easily use custom Dockerfiles) to quickly get up and running

# Why is batect better than...

## ...Vagrant?

Vagrant's use of virtual machines means that it is very heavyweight, making it difficult to run multiple projects'
environments at once. This is especially problematic on CI servers where we'd like to run multiple builds in parallel.

Furthermore, the long-lived nature of virtual machines means that it's very easy for a developer's machine to get out of sync
with the desired configuration, and nothing automatically re-provisions the machine when the configuration is changed - a
developer has to remember to re-run the provisioning step if the configuration changes.

## ...Docker Compose?

In the past, I've used Docker Compose to implement the same idea that is at the core of batect. However, using Docker Compose
for this purpose has a number of drawbacks. In particular, Docker Compose is geared towards configuring an application and its
dependencies and deploying this whole stack to something like Docker Swarm. Its CLI is designed with this purpose in mind, making
it frustrating to use day-to-day as a development tool and necessitating the use of a higher-level script to automate its usage.
It also does not elegantly support pulling together a set of containers in different configurations (eg. integration vs journey
testing), does each operation serially (instead of parallelising operations where possible) and has
[one long-standing bug](https://github.com/docker/compose/issues/4369) that makes waiting for containers to report as healthy
difficult.

## Feedback

Please [open an issue on GitHub](https://github.com/charleskorn/batect/issues/new) if you run into a problem, or you can also
[email me your feedback](mailto:me@charleskorn.com).

You can see what new features and improvements are planned in the [roadmap](ROADMAP.md).
