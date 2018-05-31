# Contribution Guidelines

Welcome and thanks for your help.

These guidelines are a work in progress. In the meantime here are some
instructions to setup the project so you can start contributing to it.

## I want to help out, what should I do?

It's entirely up to you, do whatever you're most interested in. Some suggestions:

* take a look at any open [issues](https://github.com/charleskorn/batect/issues?q=is%3Aopen+is%3Aissue), especially those tagged with
  ['good first issue'](https://github.com/charleskorn/batect/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22)
* take a look at the [roadmap](https://github.com/charleskorn/batect/blob/master/ROADMAP.md) and pick a feature you'd like to implement
* improve the [documentation](https://batect.charleskorn.com/) - add further examples, tips and tricks or clarify sections
* create a sample project for a language or framework that doesn't already have one

## Project Setup

### What's required

* JDK (Version 8 or higher)
* Yarn (Version 1.5.1 or higher)
* Git (Version 2.17.0)

## Usage

### Building the application

`./gradlew build`

### Running the unit tests and linter

`./gradlew check`

Or, to run the tests and linter once and then automatically re-run them when the code changes:

`./gradlew --continuous check`

### Fixing linting issues

`./gradlew spotlessApply`

### Running the journey tests

`./gradlew journeyTest`

## Pull request guidelines

All pull requests are welcome and warmly encouraged. Some things to keep in mind:

* all code changes should have accompanying unit tests (or, if it is not possible or reasonable to unit test the functionality or bugfix in question,
  one or more journey tests)
* the Travis build should pass - this will be triggered automatically when you submit your pull request
* if you've added a new feature or changed the behaviour of an existing feature, please update the documentation to reflect this
