# Batect roadmap

This file reflects my current plans. Something being listed here does not guarantee that I will implement it soon (or even ever),
and, similarly, just because something isn't here doesn't mean I won't ever implement it.

If there's something you're really keen to see, pull requests are always welcome :) Check out the [contributing guide](CONTRIBUTING.md) for some hints on where to start.

## v1.0

### Features
* automatically enable `--no-color` or `--simple-output` if console doesn't support it (use terminfo database rather than current detection system)
* performance improvements
  * print updates to the console asynchronously (they currently block whatever thread posts the event or is starting the step)
  * batch up printing updates to the console when using fancy output mode, rather than reprinting progress information on every event
* `brew doctor` equivalent (`./batect doctor`? `lint`?)
  * warn when using an image without a tag or with tag `latest`
  * warn when mounting files / directories in non-read-only modes without `run_as_current_user` enabled
  * warn when mounting a directory in the same location as the home directory from `run_as_current_user`
  * warn when proxy environment variables aren't in URL format or don't have the `http` or `https` schemes
  * warn when proxy settings for daemon don't match local environment (can get this through API)
  * warn when a container used as a dependency does not have a health check defined
  * warn when containers or tasks don't follow expected naming convention
* support for Windows
  * send updated console dimensions to daemon if console is resized while container is running
  * fix issue where app appears to hang when running on a 32-bit JVM (field size / alignment issue in named pipes calls?)
  * show more detailed Windows version information by reading it from `kernel32.dll`: https://stackoverflow.com/a/27323983/1668119
  * add option for isolation mode - apply to both builds and running containers
* fix #10 (proxies that refer to localhost)
  * Linux:
    * Can get IP of host from `[0].IPAM.Config.Gateway` value from running `docker network inspect <network ID>`
    * Need to run `sudo iptables -I INPUT -i docker0 -j ACCEPT` to allow containers to access host on Linux (but non-default networks don't use `docker0`,
      they use a different interface, so this command needs to be adjusted to match)
    * Local proxy needs to be listening on correct IP(s) - need to warn users about this and about exposing them to the outside world (and thus allowing other people to access their proxy)
* some way to clean up old images when they're no longer needed
* some way to reference another Dockerfile as the base image for a Dockerfile
* support setting `ulimit` values (`--ulimit` - https://docs.docker.com/engine/reference/commandline/run/#set-ulimits-in-container---ulimit)
* show build context upload progress when building image
* some way to kill a misbehaving task (eg. one that is not responding to Ctrl+C)
* shell tab completion for options (eg. `batect --h<tab>` completes to `batect --help`) - #116 ![good first issue](https://img.shields.io/badge/-good%20first%20issue-green)
* shell tab completion for tasks (eg. `batect b<tab>` completes to `batect build`) - #116 ![good first issue](https://img.shields.io/badge/-good%20first%20issue-green)
* Kubernetes-style health checks from outside the container (don't require `curl` / `wget` to be installed in the container, just provide HTTP endpoint)
* ability to build one or more container images separate to running a task (two use cases: build and push an application image, and pre-build all CI environment images in parallel rather than waiting until they're needed and building them effectively serially)

### Other
* logo
* switch to Kotlin's built-in `Result` where appropriate
* use Detekt for static analysis
* PEP8 formatting check for Python code
* add something like https://github.com/find-sec-bugs/find-sec-bugs
* fail CI build on warnings
* fail CI build on pending tests
* for fatal exceptions (ie. crashes), add information on where to report the error (ie. GitHub issue)
* documentation
  * add page explaining basic concepts (eg. explain what a task and a container are)
  * add check for broken internal or external links
  * examples for common languages and scenarios ![good first issue](https://img.shields.io/badge/-good%20first%20issue-green)
    * Android app
  * add FAQs
    * when to mount files / directories as a volume, and when to copy them into the image
    * how to run something when the container starts, regardless of the task's command line (eg. `ENTRYPOINT` with shell script and `exec`, similar to the example in [the docs](https://docs.docker.com/engine/reference/builder/#entrypoint))
  * importance of idempotency
  * add note about increasing default CPU and memory limits when using Docker on macOS
  * how to introduce Batect to an existing project
  * how to use Batect as the basis for a pipeline made up of reusable building blocks
  * expand comparison with other tools to cover Cage and Toast
  * expand comparison to cover multi-stage builds
* switch to coroutines for parallel execution?
* finish configuration code simplification (first three need https://github.com/Kotlin/kotlinx.serialization/issues/315 to be fixed)
  * Move common stuff when parsing a string (eg. EnvironmentVariableExpression, Command, Duration) out to a common class
  * Move common stuff when parsing a string or object (eg. port mapping or volume mount) out to a common class
  * Move common stuff when reading a list out to a common class (DependencySetDeserializer and PrerequisiteListDeserializer)
* security scanning for Docker images in tests and sample projects
* use Batect to build Batect (self-hosting)
* tool to visualise execution on a timeline
  * tab to show configuration as parsed
* switch to [MockK](https://github.com/oleksiyp/mockk) - Kotlin specific library with clearer upgrade path to Kotlin/Native
  * remove MockMaker resource file
* add running integration tests against Minikube to CI - can't currently easily be done as Travis doesn't support nested virtualisation and bare-metal minikube doesn't set up its own Docker daemon like it does when running in a VM
* move to Kotlin/Native
  * Why? Don't want to require users to install a JVM to use Batect, also want to remove as much overhead as possible

#### Things that would have to be changed when moving to Kotlin/Native
* file I/O and path resolution logic
* process creation / monitoring
* HTTP communication

#### Things blocking move to Kotlin/Native
* unit testing support and associated library
* file I/O support
* process creation / monitoring support

## Future improvements
* warn if dependency exits before task finishes (include exit code) ![good first issue](https://img.shields.io/badge/-good%20first%20issue-green)
* enhancements to running multiple containers at once (eg. stereotypical 'run' configuration that starts up the service with its dependencies)
  * exit options (close all after any container stops, wait for all to stop)
  * return code options (any non-zero, particular container, first to exit)
* wildcard includes (eg. `include: containers/*.yaml`) ![good first issue](https://img.shields.io/badge/-good%20first%20issue-green)
* when starting up containers and displaying progress, show countdown to health check (eg. 'waiting for container to become healthy, next check in 3 seconds, will timeout after 2 more retries')
* default to just terminating all containers at clean up time with option to gracefully shut down on individual containers
  (eg. database where data is shared between invocations and we don't want to corrupt it)
* add dependency relationship between containers and tasks (eg. running the app container requires running the build first - removes the need to specify
  build task as a prerequisite on every task that starts the app)
* some way to check for outdated base images (eg. using `postgres:10.0` and suggests updating to `postgres:10.5`)
  * [already added to Renovate](https://github.com/renovatebot/renovate/issues/7891)
  * contribute support for Batect to Dependabot ![good first issue](https://img.shields.io/badge/-good%20first%20issue-green)
* make the last mile easier: pushing images and deploying applications
* init containers: containers that must start, run and complete before a container can start (eg. populating a database with data)
* some way to handle secrets easily
* merge or replace containers or tasks when including files
  * eg. scenario described in [Cam's chat message](https://spectrum.chat/batect/general/workflows-for-making-changes-across-multiple-repos~99e11eee-cc8a-4118-ba4c-52d8e188813a)
* easy way to run all containers with output going to a tool like Seq (eg. `./batect --output=seq my-task` starts a Seq instance and uses Docker to send all containers' output there)
* IDE integration
  * eg. language server (https://microsoft.github.io/language-server-protocol/) ![good first issue](https://img.shields.io/badge/-good%20first%20issue-green)
* pull / clone Git repositories in parallel
  * how would this work with repositories that require interactive authentication?
* better integration with GitHub actions using [workflow commands](https://docs.github.com/en/actions/reference/workflow-commands-for-github-actions)
  * configuration errors
  * errors from tasks ![good first issue](https://img.shields.io/badge/-good%20first%20issue-green)
* wrap text in interpolated output mode ![good first issue](https://img.shields.io/badge/-good%20first%20issue-green)
