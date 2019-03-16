# batect roadmap

This file reflects my current plans. Something being listed here does not guarantee that I will implement it soon (or even ever),
and, similarly, just because something isn't here doesn't mean I won't ever implement it.

If there's something you're really keen to see, pull requests are always welcome :)

## v1.0

### Features
* automatically enable `--no-color` or `--simple-output` if console doesn't support it (use terminfo database rather than current detection system)
* performance improvements
  * prioritise running steps that lie on the critical path (eg. favour pulling image for leaf of dependency graph over creating container for task container)
  * print updates to the console asynchronously (they currently block whatever thread posts the event or is starting the step)
  * batch up printing updates to the console when using fancy output mode, rather than reprinting progress information on every event
  * improve performance (and reduce flickering) by only printing updated lines when updating startup progress
* check that Docker client and server are compatible versions
* `brew doctor` equivalent (`./batect doctor`? `lint`?)
  * warn when using an image without a tag or with tag `latest`
  * warn when mounting files / directories in non-read-only modes without `run_as_current_user` enabled
  * warn when mounting a directory in the same location as the home directory from `run_as_current_user`
  * warn when proxy environment variables aren't in URL format or don't have the `http` or `https` schemes
  * warn when proxy settings for daemon don't match local environment (can get this through API)
  * warn when a container used as a dependency does not have a health check defined
* support for Windows
* fix #10 (proxies that refer to localhost)
  * Linux:
    * Can get IP of host from `[0].IPAM.Config.Gateway` value from running `docker network inspect <network ID>`
    * Need to run `sudo iptables -I INPUT -i docker0 -j ACCEPT` to allow containers to access host on Linux (but non-default networks don't use `docker0`,
      they use a different interface, so this command needs to be adjusted to match)
    * Local proxy needs to be listening on correct IP(s) - need to warn users about this and about exposing them to the outside world (and thus allowing other people to access their proxy)
* some way to clean up old images when they're no longer needed
* allow tasks to not start any containers if they just have prerequisites (eg. pre-commit task)
* support build arguments
* some way to reference another Dockerfile as the base image for a Dockerfile
* reintroduce image tagging

### Other
* replace factories with references to constructors
* switch to Kotlin's built-in `Result` where appropriate
* use Detekt for static analysis
* fail CI build on warnings
* fail CI build on pending tests
* for fatal exceptions (ie. crashes), add information on where to report the error (ie. GitHub issue)
* documentation
  * add page explaining basic concepts (eg. explain what a task and a container are)
  * rename 'quick start' to 'setup'
  * add check for broken internal or external links
  * examples for common languages and scenarios
    * NodeJS
      * frontend
      * backend
      * run `yarn install` as prerequisite before each task and explain performance benefit of using Yarn over NPM
    * Android app
    * pushing app to Kubernetes
  * add FAQs
    * when to mount files / directories as a volume, and when to copy them into the image
    * how to run something when the container starts, regardless of the task's command line (eg. `ENTRYPOINT` with shell script and `exec`, similar to the example in [the docs](https://docs.docker.com/engine/reference/builder/#entrypoint))
  * importance of idempotency
  * improve the getting started guide (it's way too wordy)
  * "what's going on beneath the hood?" - explain the task lifecycle (read config, construct graph, pull images / build images, create network, start containers, wait for healthy etc.)
  * add note about increasing default CPU and memory limits when using Docker on OS X
  * how to introduce batect to an existing project
  * how to use batect as the basis for a pipeline made up of reusable building blocks
* switch to coroutines for parallel execution?
* listen for `SIGWINCH` globally and update `ConsoleInfo.dimensions` only when required rather than calling `ioctl()` every time
* test against a variety of Docker versions (eg. earliest supported version and latest)
* finish configuration code simplification (first three need https://github.com/Kotlin/kotlinx.serialization/issues/315 to be fixed)
  * Move common stuff when parsing a string (eg. EnvironmentVariableExpression, Command, Duration) out to a common class
  * Move common stuff when parsing a string or object (eg. port mapping or volume mount) out to a common class
  * Move common stuff when reading a list out to a common class (DependencySetDeserializer and PrerequisiteListDeserializer)
* analytics / metrics
* use batect to build batect (self-hosting)
* automate updating sample projects with new batect version
* tool to visualise execution on a timeline
  * tab to show configuration as parsed
* switch to [MockK](https://github.com/oleksiyp/mockk) - Kotlin specific library with clearer upgrade path to Kotlin/Native
  * remove MockMaker resource file
* move to Kotlin/Native
  * Why? Don't want to require users to install a JVM to use batect, also want to remove as much overhead as possible

#### Things that would have to be changed when moving to Kotlin/Native
* file I/O and path resolution logic
* process creation / monitoring
* HTTP communication
* logging - need to switch from Jackson to kotlinx.serialization

#### Things blocking move to Kotlin/Native
* unit testing support and associated library
* file I/O support
* process creation / monitoring support

## Future improvements
* warn if dependency exits before task finishes (include exit code)
* running multiple containers at once (eg. stereotypical 'run' configuration that starts up the service with its dependencies)
  * exit options (close all after any container stops, wait for all to stop)
  * rather than showing output from target, show output from all containers
  * logging options (all or particular container)
  * return code options (any non-zero, particular container, first to exit)
* allow configuration includes (ie. allow splitting the configuration over multiple files)
* wildcard includes (eg. `include: containers/*.yaml`)
* handle expanded form of mappings for environment variables, for example:

  ```yaml
  containers:
    build-env:
      build_dir: build-env
      environment:
        - name: THING
          value: thing_value

  ```

* support port ranges in mappings
* support protocols other than TCP in port mappings
* shell tab completion for options (eg. `batect --h<tab>` completes to `batect --help`)
* shell tab completion for tasks (eg. `batect b<tab>` completes to `batect build`)
* requires / provides relationships (eg. 'app' requires 'service-a', and 'service-a-fake' and 'service-a-real' provide 'service-a')
* don't do all path resolution up-front
  * if not all containers are used, doesn't make sense to try to resolve their paths
  * would save some time
  * means user doesn't see irrelevant error messages
* when starting up containers and displaying progress, show countdown to health check (eg. 'waiting for container to become healthy, next check in 3 seconds, will timeout after 2 more retries')
* default to just terminating all containers at clean up time with option to gracefully shut down on individual containers
  (eg. database where data is shared between invocations and we don't want to corrupt it)
* group display of options shown when running `batect --help`
* add dependency relationship between containers and tasks (eg. running the app container requires running the build first - removes the need to specify
  build task as a prerequisite on every task that starts the app)
* allow piping files into tasks (eg. `cat thefile.txt | ./batect the-task`)
  * would require:
     * creating container in non-TTY mode
     * not putting input and output in raw mode
     * not monitoring console size changes
     * streaming I/O to container in multiplexed mode (see attach API documentation)
* some way to check for outdated base images (eg. using `postgres:10.0` and suggests updating to `postgres:10.5`)
* make the last mile easier: pushing images and deploying applications
* init containers: containers that must start, run and complete before a container can start (eg. populating a database with data)
