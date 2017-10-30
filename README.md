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

## batect in action

[![asciicast](https://asciinema.org/a/vBV5iQz4jqyhKPEfkiJyM4kEt.png)](https://asciinema.org/a/vBV5iQz4jqyhKPEfkiJyM4kEt)

Want to try this for yourself? Take a look at the [sample project](https://github.com/charleskorn/batect-sample).

## How do I use this?

Take a look at the [getting started guide](docs/GettingStarted.md), or consult the
[configuration file reference](docs/ConfigFile.md).

There's also a [sample project](https://github.com/charleskorn/batect-sample) you can try, and some
[tips and tricks](docs/TipsAndTricks.md) you might want to look at.

## Why would I use this?

Every application has a build environment - the tools and configuration needed to take the source code and produce an artifact
ready for use. However, setting this up can be time consuming and frustrating. Too often new team members' first experience on
a project is a few days working out which tools they need to install, and another few days of then discovering the magic
combination of tool versions that will happily coexist. The same applies equally to CI agents as well. And as the application
evolves and changes over time, maintaining and updating this environment across all developers' machines and CI agents can be
incredibly painful.

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
* using some smart dependency management logic and Docker's caching features to do all of this very, very quickly
* taking advantage of Docker's networking features to set up an isolated network for every command
* enabling you to use existing Docker images as-is (or easily use custom Dockerfiles) to quickly get up and running

## Should I start using this now?

**Short answer**: if you don't mind some rough edges

**Longer answer**: the most important features have been implemented, but there are still [some rough edges and missing pieces](ROADMAP.md).
Furthermore, there is currently only basic documentation and I cannot promise any backwards compatibility between releases. (I'm not
planning any massive breaking changes, but I don't want to guarantee this at such an early stage.)

If you do try it out, please [send me your feedback](mailto:me@charleskorn.com) and [report any issues you find](https://github.com/charleskorn/batect/issues/new).
