# batect
[![Build Status](https://img.shields.io/travis/charleskorn/batect/master.svg)](https://travis-ci.org/charleskorn/batect)
[![Coverage](https://img.shields.io/codecov/c/github/charleskorn/batect.svg)](https://codecov.io/gh/charleskorn/batect)
[![License](https://img.shields.io/github/license/charleskorn/batect.svg)](https://opensource.org/licenses/Apache-2.0)

**b**uild **a**nd **t**esting **e**nvironments as **c**ode **t**ool: Dockerised build and testing environments made easy

## The sales pitch

* Consistent, fast, repeatable, isolated builds and test runs everywhere: your computer, your colleagues' computers and on CI
* Manage dependencies for integration and end-to-end testing with ease
* No installation required, only dependencies are Bash, Docker (v17.06+) and `curl`* - onboard new team members in minutes
* Works with any language or framework, your existing CI system, and your chosen language's existing tooling
* Take advantage of existing Docker images to get started quickly

\* at the moment, a JVM is also required, but this requirement will be removed before v1.0

[![asciicast](https://asciinema.org/a/IUh0F8WS0M0NTTKhpY1DdiRr0.png)](https://asciinema.org/a/IUh0F8WS0M0NTTKhpY1DdiRr0)

## Documentation

All documentation is available on [the documentation site](https://batect.charleskorn.com). Highlights include:

* [Introduction](https://batect.charleskorn.com)
* [Getting started guide](https://batect.charleskorn.com/GettingStarted.html)
* [Configuration file reference](https://batect.charleskorn.com/config/Overview.html)
* [Sample projects](https://batect.charleskorn.com/SampleProjects.html)
* [Comparison with other tools](https://batect.charleskorn.com/Comparison.html)

If you prefer watching videos to reading documentation, you can also watch Charles introduce batect and the rationale behind it
at the [Evolution by ThoughtWorks conference](https://www.thoughtworks.com/evolution-by-thoughtworks/content#Presentations).

## Feedback

Please [open an issue on GitHub](https://github.com/charleskorn/batect/issues/new) if you run into a problem, or you can also
[email me your feedback](mailto:me@charleskorn.com).

You can see what new features and improvements are planned in the [roadmap](ROADMAP.md).

## Acknowledgements

Thank you to the following people for their bug reports, pull requests, suggestions and feedback, in alphabetical order:

* [@andeemarks](https://github.com/andeemarks)
* [@assafw](https://github.com/assafw)
* [@binkley](https://github.com/binkley)
* [@Byron-TW](https://github.com/Byron-TW)
* [@ekamara](https://github.com/ekamara)
* [@ieffyble](https://github.com/ineffyble)
* [@Letitia-May](https://github.com/Letitia-May)
* [@minnn-minnn](https://github.com/minnn-minnn)
* [@pameck](https://github.com/pameck)
* [@safiranugroho](https://github.com/safiranugroho)
* [@Sami5](https://github.com/Sami5)
* ...and everyone else who has used the tool and provided feedback offline

Thank you to YourKit for providing a complimentary copy of the [YourKit profiler](https://www.yourkit.com/java/profiler).
