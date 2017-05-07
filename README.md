# decompose

## MVP TODO

### Config file handling
* validate configuration (eg. containers referenced in tasks as dependencies and direct targets must exist, build directory must exist)
* allow configuration includes (ie. allow splitting the configuration over multiple files)
* better error message when a key (eg. a task name) is used twice (at the moment it's `Duplicate field 'duplicated_task_name'`)
* allow tasks with just containers to start (ie. no `run` entry)

### Features
* return code options (any non-zero, particular container, first to exit)
* logging options (all or particular container) - will this be implied by the presence of a `run` configuration?
* exit options (close all after any container stops, wait for all to stop)
* use an existing image, pulling if necessary
* dependencies between containers
* dependencies explicitly specified for a task
* requires / provides relationships
* running multiple containers at once
* creating an isolated network for all containers to use
* don't require command to be specified for each container in each task (allow a default to be set in the container's configuration)
* allow the user to keep containers after failure so they can examine logs
* some way to see a list of available tasks
* pass-through additional command line arguments to a `run`
* default to a configuration file path of `decompose.yml` / `crane.yml` (ie. don't require the user to specify it every time)

### Other
* rename everything to 'Crane'
* parse container command lines properly (for command line when starting container)
* make test names consistent (eg. `it("should do something")` vs `it("does something")`)
* don't do all path resolution up-front
  * if not all containers are used, doesn't make sense to try to resolve their paths
  * would save some time
  * means user doesn't see irrelevant error messages
* logging
* option to print full stack trace on non-fatal exceptions
* for fatal exceptions (ie. crashes), add information on where to report the error

## Future improvements
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
* deal with alternative volume mount specifications (eg. 'ro')
* support port ranges in mappings
* support protocols other than TCP in port mappings
* shell tab completion for tasks (eg. `decompose run b<tab>` completes to `decompose run build`)

## Things that would have to be changed when moving to Kotlin/Native

* would most likely need to replace YAML parsing code (although this would be a good opportunity to simplify it a 
  bit and do more things while parsing the document rather than afterwards)
* file I/O and path resolution logic
* process creation / monitoring
