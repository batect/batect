# decompose

## The sales pitch

_Build and test environments as code_

* Consistent, fast, repeatable, isolated environments everywhere: your computer, your colleagues' computers and on CI
* No installation required
* Only dependencies are Bash and Docker (and `curl` or `wget`?)

## MVP TODO

### Config file handling
* validate configuration (eg. containers referenced in tasks as dependencies and direct targets must exist)
* better error message when a key (eg. a task name) is used twice (at the moment it's `Duplicate field 'duplicated_task_name'`)
* allow tasks with just containers to start (ie. no `run` entry)
* warn if a dependency is specified twice (either for a task or for a container)

### Features
* logging options (all or particular container) - will this be implied by the presence of a `run` configuration?
* exit options (close all after any container stops, wait for all to stop)
* just use an existing image, pulling if necessary (ie. don't require a local Dockerfile)
* dependencies between containers
* warn if dependency exits before task finishes
* running multiple containers at once (eg. stereotypical 'run' configuration that starts up the service with its dependencies)
  * rather than showing output from target, show output from all containers
* don't require command to be specified for each container in each task (allow a default to be set in the container's configuration)
* allow the user to keep containers after failure so they can examine logs (or even default to not destroying anything if they fail)
  * always clean up dependency containers when running on CI by default (use CI environment variable to detect, add command-line switch to disable) 
* some way to add descriptions to tasks, which are then shown in `crane tasks`
* run image builds in parallel and only show summary of build progress (unless image build fails, in which case show full output)
* start dependencies in parallel
* overridable health check parameters for containers (so that you can have the health check poll very frequently when waiting for something to 
  come up for tests, but less frequently if that container is used in production)
* if a dependency container fails to start, show output and exit code from last health check attempt 
* some way to propagate environment variables from host environment to target environment
* some way to add additional environment variables at the task level (for the target container only, not dependencies)
* flag (eg. `--quiet`) to only show output from task

### Other
* rename everything to 'Crane'
* make test names consistent (eg. `it("should do something")` vs `it("does something")`)
* logging (for Crane internals)
* option to print full stack trace on non-fatal exceptions
* command to print version and system info
* for fatal exceptions (ie. crashes), add information on where to report the error (ie. GitHub issue)
* use Docker API directly rather than using Docker CLI
* documentation
* examples (update or remove `sample` directory)
* use `--iidfile` to get image ID after build and stop relying on tag
* wrapper script to pull appropriate binary down (like `gradlew`)
  * should be OS independent (so it can be committed with code) and pull down correct binary
  * should lock to particular version (how to warn about newer available version?)
  * should not require anything beyond what would already be installed on a standard OS X or Linux install (Bash and `curl` or `wget`)

## Future improvements
* return code options (any non-zero, particular container, first to exit)
* allow configuration includes (ie. allow splitting the configuration over multiple files)
* handle expanded form of mappings, for example:
  
  ```yaml
  containers:
    build-env:
      build_dir: build-env
      environment:
        - name: THING
          value: thing_value
  
  ```

* wildcard includes
* support alternative volume mount specifications (eg. 'ro')
* support port ranges in mappings
* support protocols other than TCP in port mappings
* shell tab completion for tasks (eg. `decompose run b<tab>` completes to `decompose run build`)
* pass-through additional command line arguments to a `run`
* requires / provides relationships (eg. 'app' requires 'service-a', and 'service-a-fake' and 'service-a-real' provide 'service-a')
* prerequisites for tasks (eg. run the build before running journey tests)
* support for Windows
* don't do all path resolution up-front
  * if not all containers are used, doesn't make sense to try to resolve their paths
  * would save some time
  * means user doesn't see irrelevant error messages

## Things that would have to be changed when moving to Kotlin/Native

* would most likely need to replace YAML parsing code (although this would be a good opportunity to simplify it a 
  bit and do more things while parsing the document rather than afterwards)
* file I/O and path resolution logic
* process creation / monitoring
