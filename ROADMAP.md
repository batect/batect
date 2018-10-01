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
* show a short summary after a task finishes (eg. `build finished with exit code X in 2.3 seconds`)
* support for Windows
* show progress information when cleaning up temporary files or directories in fancy output mode
* fix #10 (proxies that refer to localhost)
  * Linux:
    * Can get IP of host from `[0].IPAM.Config.Gateway` value from running `docker network inspect <network ID>`
    * Need to run `sudo iptables -I INPUT -i docker0 -j ACCEPT` to allow containers to access host on Linux (but non-default networks don't use `docker0`,
      they use a different interface, so this command needs to be adjusted to match)
    * Local proxy needs to be listening on correct IP(s) - need to warn users about this and about exposing them to the outside world (and thus allowing other people to access their proxy)
* 'did you mean...' suggestions when requested task doesn't exist (eg. user runs `./batect unittest`, suggests `unit-test` might be what they meant)
* some way to clean up old images when they're no longer needed
* handle the user pressing Ctrl-C during startup or cleanup
* allow tasks to not start any containers if they just have prerequisites (eg. pre-commit task)
* allow specifying default values for environment variables
* do as much validation at configuration loading time as possible (eg. validate command lines are syntactically valid, environment variable expressions are syntactically valid) - this allows us to
  include line numbers etc.

### Bugs
* ensure prerequisite order is respected, even with multiple dependencies

### Other
* replace factories with references to constructors
* switch to Kotlin's built-in `Result` where appropriate
* reintroduce image tagging
* for fatal exceptions (ie. crashes), add information on where to report the error (ie. GitHub issue)
* use Docker API directly rather than using Docker CLI (would allow for more detailed progress and error reporting)
* use tmpfs for home directories? (https://docs.docker.com/engine/reference/run/#tmpfs-mount-tmpfs-filesystems)
* rework console printing stuff so that tests aren't coupled to the particular choice of how messages are broken into `print()` calls (eg. introduce some kind of abstract representation of formatted text)
  * ContainerStartupProgressLineSpec is an example of the issue at the moment
* documentation
  * add check for broken internal or external links
  * examples for common languages and scenarios
    * Golang
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
  * explain the task lifecycle (read config, construct graph, pull images / build images, start containers, wait for healthy etc.)
  * add note about increasing default CPU and memory limits when using Docker on OS X
* make error message formatting (eg. image build failed, container could not start) prettier and match other output (eg. use of bold for container names)
* test against a variety of Docker versions (eg. earliest supported version and latest)
* use batect to build batect (self-hosting)
* tool to visualise execution on a timeline
  * tab to show configuration as parsed
* switch to [MockK](https://github.com/oleksiyp/mockk) - Kotlin specific library with clearer upgrade path to Kotlin/Native
  * remove MockMaker resource file
* move to Kotlin/Native
  * Why? Don't want to require users to install a JVM to use batect, also want to remove as much overhead as possible

#### Things that would have to be changed when moving to Kotlin/Native
* would most likely need to replace YAML parsing code (although this would be a good opportunity to simplify it a
  bit and do more things while parsing the document rather than afterwards)
* file I/O and path resolution logic
* process creation / monitoring

#### Things blocking move to Kotlin/Native
* unit testing support and associated library
* file I/O support
* process creation / monitoring support
* YAML parsing library

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
* warn if a dependency does not have a health check defined
* default to just terminating all containers at clean up time with option to gracefully shut down on individual containers
  (eg. database where data is shared between invocations and we don't want to corrupt it)
* some way to group tasks shown when running `batect --list-tasks`
* group display of options shown when running `batect --help`
* add dependency relationship between containers and tasks (eg. running the app container requires running the build first - removes the need to specify
  build task as a prerequisite on every task that starts the app)
