# Batect
[![Build Status](https://github.com/batect/batect/workflows/CI/badge.svg)](https://github.com/batect/batect/actions?query=workflow%3ACI+branch%3Amain)
[![Coverage](https://img.shields.io/codecov/c/github/batect/batect.svg)](https://codecov.io/gh/batect/batect)
[![CII Best Practices](https://bestpractices.coreinfrastructure.org/projects/2698/badge)](https://bestpractices.coreinfrastructure.org/projects/2698)
[![License](https://img.shields.io/github/license/batect/batect.svg)](https://opensource.org/licenses/Apache-2.0)
[![Chat](https://img.shields.io/badge/chat-on%20GitHub%20Discussions-brightgreen.svg)](https://github.com/batect/batect/discussions)

**b**uild **a**nd **t**esting **e**nvironments as **c**ode **t**ool

Batect allows you to define your development tasks (building, running, testing, linting and more) in terms of one or more
Docker containers, run those tasks quickly and consistently everywhere, and easily share them with your team.

Batect is:

* :rocket: **fast**: Tasks start quickly due to parallelisation, run quickly thanks to caching, and clean up reliably every time - we've
  seen 17% quicker execution than Docker Compose.

* :relieved: **easy to use**: Easily share your development tasks with your whole team, and free them from manual setup of build tools and dependencies
  for tasks like running your app locally or integration testing. And no installation is required either - just drop the script in your
  project and Batect takes care of the rest.

* :sparkles: **consistent**: Batect uses Docker to create a clean, isolated environment every time you run a task, freeing you from "works on my machine"
  issues - including on CI. And you can easily share tasks between projects with bundles.

* :white_check_mark: **versatile**: Anything that can run in a Docker container can be run with Batect - builds, unit testing, integration testing, linting,
  local environments, deployments; frontend, backend or somewhere in between, Batect can do it all.

[![asciicast](https://asciinema.org/a/714gRQsQW1VDHQMuWzwRuAdU4.svg)](https://asciinema.org/a/714gRQsQW1VDHQMuWzwRuAdU4)

## Hello World

The simplest possible `batect.yml`:

```yaml
containers:
  my-container:
    image: alpine:3.11.3

tasks:
  say-hello:
    description: Say hello to the nice person reading the Batect README
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
- say-hello: Say hello to the nice person reading the Batect README
```

Take a look at the [sample projects](https://batect.dev/docs/getting-started/sample-projects/) for more examples.

## Getting started

1. Download the latest version of `batect` and `batect.cmd` from the [releases page](https://github.com/batect/batect/releases),
   and copy them into your project.

    Note that you only need the scripts - you don't need to download `batect.jar`.

    The `batect` and `batect.cmd` scripts are designed to be committed alongside your project, and not installed globally. Committing
    them alongside your code improves consistency within your team, as everyone uses the same version of Batect. They will
    automatically pull down the correct version of Batect for your operating system.

2. If you're on Linux or macOS, make sure the script is executable: run `chmod +x batect`.

3. Create your `batect.yml` to define your tasks and the environments they run in:
    * Take a look at the [sample projects](https://batect.dev/docs/getting-started/sample-projects/) for inspiration
    * Dive straight into [the configuration file reference](https://batect.dev/docs/reference/config)
    * Follow the [getting started tutorial](https://batect.dev/docs/getting-started/tutorial)
    * Or, if you're using another tool already and want to switch to Batect,
      [batectify](https://batectify.enchanting.dev/) by [@ineffyble](https://github.com/ineffyble) can convert files from
      other tools to Batect's format

## Requirements

Batect requires Docker 18.03.1 or newer, Java 8 or newer (although this requirement will be removed before v1.0), and:

* On Linux and macOS: Bash and `curl`
* On Windows: Windows 10 / Windows Server 2016 or later

Batect supports both Linux and Windows containers.

A 64-bit version of Java is required on Windows.

## Under the hood

Take a look at [the task lifecycle](https://batect.dev/docs/concepts/task-lifecycle) to understand how Batect executes tasks.

## Documentation

All documentation is available on [the documentation site](https://batect.dev). Highlights include:

* [Introduction](https://batect.dev/docs/)
* [Getting started tutorial](https://batect.dev/docs/getting-started/tutorial)
* [Configuration file reference](https://batect.dev/docs/reference/config)
* [Sample projects](https://batect.dev/docs/getting-started/sample-projects)
* [Comparison with other tools](https://batect.dev/docs/getting-started/comparison)

## Presentations

* *Dockerised local build and testing environments made easy* at Container Camp AU (July 2019): [video](https://www.youtube.com/watch?v=qNzv7IuTp50)

  Also presented at DevOpsDays Auckland (October 2019), DDD Sydney (September 2019) and DDD Melbourne (August 2019).

* *Build & Testing Environments as Code: Because Life's Too Short Not To* at Evolution by ThoughtWorks (June 2018):
  [video](https://www.thoughtworks.com/evolution-by-thoughtworks/content#Presentations),
  [slides](https://www.slideshare.net/ThoughtWorks/charles-korn-build-testing-environments-as-code-because-lifes-too-short-not-to-evolution-102970374)

## Support and community

We use [GitHub Discussions](https://github.com/batect/batect/discussions) for community support and Q&A.

## Feedback

Please [open an issue on GitHub](https://github.com/batect/batect/issues/new) if you run into a problem or have a suggestion.

You can see what new features and improvements are planned in the [roadmap](ROADMAP.md).

## Contributing

See [the contribution guide](CONTRIBUTING.md).

## Acknowledgements

Thank you to the following people for their bug reports, pull requests, suggestions and feedback, in alphabetical order:

<!-- CONTRIBUTOR_LIST_STARTS_HERE -->
[@Abhisha1](https://github.com/Abhisha1),
[@aidansteele](https://github.com/aidansteele),
[@akamanocha](https://github.com/akamanocha),
[@alexswilliams](https://github.com/alexswilliams),
[@alpha-er](https://github.com/alpha-er),
[@andeemarks](https://github.com/andeemarks),
[@asharma8438](https://github.com/asharma8438),
[@askfor](https://github.com/askfor),
[@assafw](https://github.com/assafw),
[@b-a-byte](https://github.com/b-a-byte),
[@BethanyDrake-x](https://github.com/BethanyDrake-x),
[@binkley](https://github.com/binkley),
[@boxleytw](https://github.com/boxleytw),
[@Byron-TW](https://github.com/Byron-TW),
[@camjackson](https://github.com/camjackson),
[@carloslimasis](https://github.com/carloslimasis),
[@catacgc](https://github.com/catacgc),
[@cazgp](https://github.com/cazgp),
[@chandantp](https://github.com/chandantp),
[@chinwobble](https://github.com/chinwobble),
[@csxero](https://github.com/csxero),
[@da4089](https://github.com/da4089),
[@damian-bisignano](https://github.com/damian-bisignano),
[@DamianBis](https://github.com/DamianBis),
[@dan-neumegen-xero](https://github.com/dan-neumegen-xero),
[@DavidHe1127](https://github.com/DavidHe1127),
[@dflook](https://github.com/dflook),
[@DiegoAlpizar](https://github.com/DiegoAlpizar),
[@diffidentDude](https://github.com/diffidentDude),
[@diwang-xero](https://github.com/diwang-xero),
[@doug-ferris-mondo](https://github.com/doug-ferris-mondo),
[@eichelkrauta](https://github.com/eichelkrauta),
[@ekamara](https://github.com/ekamara),
[@erMaurone](https://github.com/erMaurone),
[@frglrock](https://github.com/frglrock),
[@fwilhe2](https://github.com/fwilhe2),
[@gabrielsadaka](https://github.com/gabrielsadaka),
[@GerardWorks](https://github.com/GerardWorks),
[@GoodDingo](https://github.com/GoodDingo),
[@heyheman11](https://github.com/heyheman11),
[@hongyuanlei](https://github.com/hongyuanlei),
[@hpcsc](https://github.com/hpcsc),
[@hussein-joe](https://github.com/hussein-joe),
[@ineffyble](https://github.com/ineffyble),
[@jagregory](https://github.com/jagregory),
[@jbduncan](https://github.com/jbduncan),
[@jmewes](https://github.com/jmewes),
[@jobasiimwe](https://github.com/jobasiimwe),
[@Letitia-May](https://github.com/Letitia-May),
[@mario-prabowo-xero](https://github.com/mario-prabowo-xero),
[@marty-macfly](https://github.com/marty-macfly),
[@mdlnr](https://github.com/mdlnr),
[@minnn-minnn](https://github.com/minnn-minnn),
[@mjstrasser](https://github.com/mjstrasser),
[@Mubashwer](https://github.com/Mubashwer),
[@mylesmacrae](https://github.com/mylesmacrae),
[@nashvan](https://github.com/nashvan),
[@nesl247](https://github.com/nesl247),
[@nkrul](https://github.com/nkrul),
[@pameck](https://github.com/pameck),
[@paulvalla-zorro](https://github.com/paulvalla-zorro),
[@priorax](https://github.com/priorax),
[@ryanb6920](https://github.com/ryanb6920),
[@safiranugroho](https://github.com/safiranugroho),
[@Sami5](https://github.com/Sami5),
[@smozely](https://github.com/smozely),
[@SongGithub](https://github.com/SongGithub),
[@squirmy](https://github.com/squirmy),
[@TassSinclair](https://github.com/TassSinclair),
[@thirkcircus](https://github.com/thirkcircus),
[@Tzrlk](https://github.com/Tzrlk),
[@wandrewni](https://github.com/wandrewni),
[@wilvk](https://github.com/wilvk),
[@wyvern8](https://github.com/wyvern8),
[@xdavidnguyen](https://github.com/xdavidnguyen),
[@yoyo-i3](https://github.com/yoyo-i3),
[@ZhuYeXero](https://github.com/ZhuYeXero),
[@zizhongzhang](https://github.com/zizhongzhang), and everyone else who has used the tool and provided feedback offline
<!-- CONTRIBUTOR_LIST_ENDS_HERE -->

Thank you to YourKit for providing a complimentary copy of the [YourKit profiler](https://www.yourkit.com/java/profiler), and
thank you to JFrog for providing a complimentary instance of both [Bintray](https://bintray.com/batect/batect/batect) and
[Artifactory](https://jfrog.com/artifactory/).
