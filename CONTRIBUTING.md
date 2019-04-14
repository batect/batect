# Contribution Guidelines

Welcome and thanks for your interest in helping improve batect!

These guidelines are a work in progress. If there is some information that you'd like to see below,
please [submit an issue](https://github.com/charleskorn/batect/issues/new).

## I want to help out, what should I do?

It's entirely up to you, do whatever you're most interested in. Some suggestions:

* take a look at any open [issues](https://github.com/charleskorn/batect/issues?q=is%3Aopen+is%3Aissue), especially those tagged with
  ['good first issue'](https://github.com/charleskorn/batect/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22)
* take a look at the [roadmap](https://github.com/charleskorn/batect/blob/master/ROADMAP.md) and pick a feature you'd like to implement
* improve the [documentation](https://batect.charleskorn.com/) - add further examples, tips and tricks or clarify sections
* create a [sample project](https://batect.charleskorn.com/SampleProjects.html) for a language or framework that doesn't already have one

## Prerequisites

* JDK (version 8 or higher)
* Git (version 2.17.0 or higher)
* Docker (any version compatible with batect)

## Usage

### Building the application

`./gradlew build` will compile the application, while `./gradlew installShadowDist` will compile and assemble batect (you can then invoke batect
with `./app/build/install/app-shadow/bin/batect`).

### Running the unit tests and linter

`./gradlew check`

Or, to run the tests and linter once and then automatically re-run them when the code changes:

`./gradlew --continuous check`

### Fixing linting issues

`./gradlew spotlessApply`

### Running the integration tests

`./gradlew integrationTest`

### Running the journey tests

`./gradlew journeyTest`

## Pull request guidelines

All pull requests are welcome and warmly encouraged. Some things to keep in mind:

* all code changes should have accompanying unit tests (or, if it is not possible or reasonable to unit test the functionality or bugfix in question,
  one or more journey tests)
* the Travis build should pass - this will be triggered automatically when you submit your PR
* if you've added a new feature or changed the behaviour of an existing feature, please update the documentation to reflect this
* please keep PRs limited to a single bugfix or feature - if your PR fixes multiple issues or adds multiple features, please submit a separate PR for each
* submitting work-in-progress PRs for feedback is welcome and encouraged
