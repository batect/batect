# batect
[![Build Status](https://img.shields.io/travis/batect/batect/master.svg)](https://travis-ci.com/batect/batect)
[![Coverage](https://img.shields.io/codecov/c/github/batect/batect.svg)](https://codecov.io/gh/batect/batect)
[![License](https://img.shields.io/github/license/batect/batect.svg)](https://opensource.org/licenses/Apache-2.0)
[![Chat](https://img.shields.io/badge/chat-on%20spectrum-brightgreen.svg)](https://spectrum.chat/batect)

**b**uild **a**nd **t**esting **e**nvironments as **c**ode **t**ool: Dockerised build and testing environments made easy

## The sales pitch

* Consistent, fast, repeatable, isolated builds and test runs everywhere: your computer, your colleagues' computers and on CI
* Document and share common tasks within your team in a structured way - it's a
  [go script](https://www.thoughtworks.com/insights/blog/praise-go-script-part-i) based on Docker
* Manage dependencies for integration and end-to-end testing (like databases) with ease
* Onboard new team members in minutes: no installation required
* Supports Linux, macOS and Windows
* Works with any language or framework, your existing CI system, and your chosen language's existing tooling
* Take advantage of existing Docker images to get started quickly

[![asciicast](https://asciinema.org/a/714gRQsQW1VDHQMuWzwRuAdU4.svg)](https://asciinema.org/a/714gRQsQW1VDHQMuWzwRuAdU4)

## Getting started

1. Drop the latest `batect` and `batect.cmd` scripts from the [releases page](https://github.com/batect/batect/releases)
   into the root folder of your project.
2. If you're on Linux or macOS, make sure the script is executable: run `chmod +x batect`.
3. Create your `batect.yml` to define your environment:
    * Take a look at the [sample projects](https://batect.dev/SampleProjects.html) for inspiration
    * Dive straight into [the configuration file reference](https://batect.dev/config/Overview.html)
    * Or, if you're using another tool already and want to switch to batect,
      [batectify](https://batectify.enchanting.dev/) by [@ineffyble](https://github.com/ineffyble) can convert files from
      other tools to batect's format

## System requirements

batect requires Docker 18.03.1 or newer, Java 8 or newer (although this requirement will be removed before v1.0), and:

* On Linux and macOS: Bash and `curl`
* On Windows: Windows 10

batect supports both Linux and Windows containers.

## Documentation

All documentation is available on [the documentation site](https://batect.dev). Highlights include:

* [Introduction](https://batect.dev)
* [Getting started guide](https://batect.dev/GettingStarted.html)
* [Configuration file reference](https://batect.dev/config/Overview.html)
* [Sample projects](https://batect.dev/SampleProjects.html)
* [Comparison with other tools](https://batect.dev/Comparison.html)

If you prefer watching videos to reading documentation, you can also watch Charles introduce batect and the rationale behind it
at the [Evolution by ThoughtWorks conference](https://www.thoughtworks.com/evolution-by-thoughtworks/content#Presentations).

## Support and community

There's a batect community on [Spectrum](https://spectrum.chat/batect/) - anyone is welcome to join.

## Feedback

Please [open an issue on GitHub](https://github.com/batect/batect/issues/new) if you run into a problem or have a suggestion.

You can see what new features and improvements are planned in the [roadmap](ROADMAP.md).

## Acknowledgements

Thank you to the following people for their bug reports, pull requests, suggestions and feedback, in alphabetical order:

* [@andeemarks](https://github.com/andeemarks)
* [@assafw](https://github.com/assafw)
* [@binkley](https://github.com/binkley)
* [@Byron-TW](https://github.com/Byron-TW)
* [@cazgp](https://github.com/cazgp)
* [@eichelkrauta](https://github.com/eichelkrauta)
* [@ekamara](https://github.com/ekamara)
* [@ineffyble](https://github.com/ineffyble)
* [@jagregory](https://github.com/jagregory)
* [@jobasiimwe](https://github.com/jobasiimwe)
* [@Letitia-May](https://github.com/Letitia-May)
* [@minnn-minnn](https://github.com/minnn-minnn)
* [@pameck](https://github.com/pameck)
* [@safiranugroho](https://github.com/safiranugroho)
* [@Sami5](https://github.com/Sami5)
* ...and everyone else who has used the tool and provided feedback offline

Thank you to YourKit for providing a complimentary copy of the [YourKit profiler](https://www.yourkit.com/java/profiler), and
thank you to JFrog for providing a complimentary instance of both [Bintray](https://bintray.com/batect/batect/batect) and
[Artifactory](https://jfrog.com/artifactory/).
