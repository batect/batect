# batect
[![Build Status](https://img.shields.io/travis/batect/batect/master.svg)](https://travis-ci.com/batect/batect)
[![Coverage](https://img.shields.io/codecov/c/github/batect/batect.svg)](https://codecov.io/gh/batect/batect)
[![CII Best Practices](https://bestpractices.coreinfrastructure.org/projects/2698/badge)](https://bestpractices.coreinfrastructure.org/projects/2698)
[![License](https://img.shields.io/github/license/batect/batect.svg)](https://opensource.org/licenses/Apache-2.0)
[![Chat](https://img.shields.io/badge/chat-on%20spectrum-brightgreen.svg)](https://spectrum.chat/batect)

**b**uild **a**nd **t**esting **e**nvironments as **c**ode **t**ool

batect allows you to define your development tasks (building, running, testing, linting and more) in terms of one or more
Docker containers, run those tasks quickly and consistently everywhere, and easily share them with your team.

batect is:

* :rocket: **fast**: Tasks start quickly due to parallelisation, run quickly thanks to caching, and clean up reliably every time - we've
  seen 17% quicker execution than Docker Compose.

* :relieved: **easy to use**: Easily share your development tasks with your whole team, and free them from manual setup of build tools and dependencies
  for tasks like running your app locally or integration testing. And no installation is required either - just drop the script in your
  project and batect takes care of the rest.

* :sparkles: **consistent**: batect uses Docker to create a clean, isolated environment every time you run a task, freeing you from "works on my machine"
  issues - including on CI.

* :white_check_mark: **versatile**: Anything that can run in a Docker container can be run with batect - builds, unit testing, integration testing, linting,
  local environments, deployments; frontend, backend or somewhere in between, batect can do it all.

[![asciicast](https://asciinema.org/a/714gRQsQW1VDHQMuWzwRuAdU4.svg)](https://asciinema.org/a/714gRQsQW1VDHQMuWzwRuAdU4)

## Hello World

The simplest possible `batect.yml`:

```yaml
containers:
  my-container:
    image: alpine:3.11.3

tasks:
  say-hello:
    description: Say hello to the nice person reading the batect README
    run:
      container: my-container
      command: echo 'Hello world!'
```

Run it with `./batect say-hello`:

```
$ ./batect say-hello
Running say-hello...
my-container: running echo 'Hello world!'

Hello world!

say-hello finished with exit code 0 in 1.2s.
```

Get a list of available tasks with `./batect --list-tasks`:

```
$ ./batect --list-tasks
Available tasks:
- say-hello: Say hello to the nice person reading the batect README
```

Take a look at the [sample projects](https://batect.dev/SampleProjects.html) for more examples.

## Getting started

1. Download the latest version of `batect` and `batect.cmd` from the [releases page](https://github.com/batect/batect/releases),
   and copy them into your project.

    Note that you only need the scripts - you don't need to download `batect.jar`.

    The `batect` and `batect.cmd` scripts are designed to be committed alongside your project, and not installed globally. Committing
    them alongside your code improves consistency within your team, as everyone uses the same version of batect. They will
    automatically pull down the correct version of batect for your operating system.

2. If you're on Linux or macOS, make sure the script is executable: run `chmod +x batect`.

3. Create your `batect.yml` to define your tasks and the environments they run in:
    * Take a look at the [sample projects](https://batect.dev/SampleProjects.html) for inspiration
    * Dive straight into [the configuration file reference](https://batect.dev/config/Overview.html)
    * Follow the [getting started tutorial](https://batect.dev/GettingStarted.html)
    * Or, if you're using another tool already and want to switch to batect,
      [batectify](https://batectify.enchanting.dev/) by [@ineffyble](https://github.com/ineffyble) can convert files from
      other tools to batect's format

## Requirements

batect requires Docker 18.03.1 or newer, Java 8 or newer (although this requirement will be removed before v1.0), and:

* On Linux and macOS: Bash and `curl`
* On Windows: Windows 10 / Windows Server 2016 or later

batect supports both Linux and Windows containers.

## Under the hood

Take a look at [the task lifecycle](https://batect.dev/TaskLifecycle.html) to understand how batect executes tasks.

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

## Contributing

See [the contribution guide](CONTRIBUTING.md).

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
