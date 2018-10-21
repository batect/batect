# Introduction

## What is batect?

batect is a tool that makes setting up, sharing and maintaining Docker-based development and testing environments much, much easier.

The main benefits of batect are:

* Consistent, fast, repeatable, isolated builds and test runs everywhere: your computer, your colleagues' computers and on CI
* Manage dependencies for integration and end-to-end testing with ease
* No installation required, only dependencies are Bash, Docker and `curl`* - onboard new team members in minutes
* Works with any language or framework, your existing CI system, and your chosen language's existing tooling
* Take advantage of existing Docker images to get started quickly

\* at the moment, a JVM is also required, but this requirement will be removed before v1.0

## What is batect not?

* a build tool - instead, use your chosen language's existing tooling (eg. Gradle, Rake, CMake or Cargo) from within a batect task
* a deployment tool - instead, use your target environment's existing tooling (eg. kubectl) from within a batect task
* a CI tool - instead, use your existing CI tool and have it run batect

## Why would you use batect?

Every application has a build environment - the tools and configuration needed to take the source code and produce an artifact
ready for use.

However, setting this up can be time consuming and frustrating. Too often new team members' first experience on
a project is a few days working out which tools they need to install, and another few days of then discovering the magic
combination of tool versions that will happily coexist. And as the application evolves and changes over time, maintaining and
updating this environment across all developers' machines and CI agents can be incredibly painful.

Similarly, most applications have external dependencies - for example, other services, databases, caches, credential storage
systems... the list is endless. Because of this, we would like to run integration, component or journey tests where the
application itself (or some part of it) interacts with these external dependencies.

In some cases, we'd like to use a real instance of it (eg. a running Postgres instance), and in other cases, we'd like to use a
fake (eg. a fake implementation of a downstream service). Either way, installing, configuring and managing all these dependencies
takes a lot of work, and making sure they're in a known state before a test run is key to reducing test flakiness. Add in networking
gremlins, different operating systems, personal preferences, built-up cruft and manual configuration and you end up with an enormous
number of variables that lead to a huge amount of wasted time spent debugging issues that are entirely preventable.

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
