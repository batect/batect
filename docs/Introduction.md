# Introduction

## What is batect?

batect is a tool that makes setting up, sharing and maintaining Docker-based development and testing environments much, much easier.

The main benefits of batect are:

* Consistent, fast, repeatable, isolated builds and test runs everywhere: your computer, your colleagues' computers and on CI
* Manage dependencies for integration and end-to-end testing with ease
* No installation required, only dependencies are Bash, Docker and `curl`* - onboard new team members in minutes
* Works with your existing CI system, and your chosen language's existing tooling
* Take advantage of existing Docker images to get started quickly

\* at the moment, a JVM is also required, but this requirement will be removed before v1.0

## What is batect not?

* a build tool - instead, use your chosen language's existing tooling (eg. Gradle, Rake, CMake or Cargo) from within a batect task
* a deployment tool - instead, use your target environment's existing tooling (eg. kubectl) from within a batect task
* a CI tool - instead, use your existing CI tool and have it run batect
