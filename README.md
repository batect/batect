# batect 
**b**uild **a**nd **t**esting **e**nvironments as **c**ode **t**ool

## The sales pitch

_Build and testing environments as code_

* Consistent, fast, repeatable, isolated builds and test runs everywhere: your computer, your colleagues' computers and on CI
* Manage dependencies for integration and end-to-end testing with ease
* No installation required
* Only dependencies are Bash and Docker (and `curl` or `wget`?)
* Works with your existing CI system

## MVP TODO

### Config file handling
* better error message when a key (eg. a task name) is used twice (at the moment it's `Duplicate field 'duplicated_task_name'`)
* warn if a dependency is specified twice (either for a task or for a container)

### Features
* run operations (image builds, container creation, container start) in parallel where possible
  * configurable level of parallelism
  * better output to show what's going on
* just use an existing image, pulling if necessary (ie. don't require a local Dockerfile)
* allow the user to keep containers after failure so they can examine logs
* overridable health check parameters for containers (so that you can have the health check poll very frequently when waiting for something to 
  come up for tests, but less frequently if that container is used in production)
* if a dependency container fails to become healthy, show output and exit code from last health check attempt 
* some way to propagate environment variables from host environment to target environment
* some way to add additional environment variables at the task level (for the target container only, not dependencies)
* flag (eg. `--quiet`) to only show output from task
* flag (eg. `--simple-output`) to disable fancy output formatting (eg. progress bars) from batect (task process can still do whatever it wants)
* flag (eg. `--no-colors`) to disable coloured and bold output (implies `--simple-output`) from batect (task process can still do whatever it wants)
* automatically enable `--no-colours` or `--simple-output` if console doesn't support it
* fancy progress bar output for building images and starting dependencies
  * make sure accidental input on stdin doesn't mangle it
* automatically create missing local volume mount directories and show a warning (useful when mounting a directory intended to be a cache)
* show a message when cleaning up
  * first pass: just print a simple 'Cleaning up...' message
  * second pass: show what still remains to be cleaned up (eg. 'Cleaning up... 3 containers (X, Y, Z) still running')
* easy way to mount Docker socket and statically linked binary into container (eg. for building other containers from within that container)
* default to just terminating all containers at clean up time with option to gracefully shut down on individual containers 
  (eg. database where data is shared between invocations and we don't want to corrupt it)
* prerequisites for tasks (eg. run the build before running journey tests)
* display help if command name is followed by `--help` (eg. `batect run --help`)

### Other
* make test names consistent (eg. `it("should do something")` vs `it("does something")`)
* logging (for batect internals)
* option to print full stack trace on non-fatal exceptions
* command to print version and system info
* for fatal exceptions (ie. crashes), add information on where to report the error (ie. GitHub issue)
* use Docker API directly rather than using Docker CLI (would allow for more detailed progress and error reporting)
* documentation
  * CI setup - reminder to clean up stale images regularly
  * use ':cached' mode for mounts for performance (https://docs.docker.com/docker-for-mac/osxfs-caching/)
  * importance of idempotency
* examples (update or remove `sample` directory)
* use `--iidfile` to get image ID after build and stop relying on tag
* wrapper script to pull appropriate binary down (like `gradlew`)
  * should be OS independent (so it can be committed with application code) and pull down correct binary
  * should lock to particular version (how to warn about newer available version?)
  * should not require anything beyond what would already be installed on a standard OS X or Linux install (Bash and `curl` or `wget`)
* make error message formatting (eg. image build failed, container could not start) prettier and match other output (eg. use of bold for container names)
* log error messages to stderr, not stdout
* regularly check for updates and notify the user if there is one available

## Future improvements
* warn if dependency exits before task finishes (include exit code)
* running multiple containers at once (eg. stereotypical 'run' configuration that starts up the service with its dependencies)
  * exit options (close all after any container stops, wait for all to stop)
  * rather than showing output from target, show output from all containers
  * logging options (all or particular container) 
  * return code options (any non-zero, particular container, first to exit)
* allow configuration includes (ie. allow splitting the configuration over multiple files)
* wildcard includes (eg. `include: containers/*.yaml`)
* handle expanded form of mappings, for example:
  
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
* shell tab completion for built-in commands (eg. `batect r<tab>` completes to `batect run`)
* shell tab completion for tasks (eg. `batect run b<tab>` completes to `batect run build`)
* pass-through additional command line arguments to a `run`
* requires / provides relationships (eg. 'app' requires 'service-a', and 'service-a-fake' and 'service-a-real' provide 'service-a')
* support for Windows
* don't do all path resolution up-front
  * if not all containers are used, doesn't make sense to try to resolve their paths
  * would save some time
  * means user doesn't see irrelevant error messages
* when starting up containers and displaying progress, show countdown to health check (eg. 'waiting for container to become healthy, next check in 3 seconds')
* warn if a dependency does not have a health check defined

## Things that would have to be changed when moving to Kotlin/Native

* would most likely need to replace YAML parsing code (although this would be a good opportunity to simplify it a 
  bit and do more things while parsing the document rather than afterwards)
* file I/O and path resolution logic
* process creation / monitoring

## Things blocking move to Kotlin/Native

* unit testing support and associated library
* file I/O support
* process creation / monitoring support
* YAML parsing library
* [Kodein support](https://github.com/SalomonBrys/Kodein/tree/master/kodein-native)

## Notes
* Parallelising task step execution resulted in a 10s execution time reduction for DS journey tests (63s -> 53s)
