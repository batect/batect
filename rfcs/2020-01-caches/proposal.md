# Problem statement

One of the most common complaints about running builds or tests using Dockerised environments, including with batect, is that they can be significantly slower than running them in a virtualisation-free environment.

This reduced performance primarily stems from three factors:

* the time taken to set up the Dockerised environment (eg. the time taken to create networks and containers and then start containers)
* the time taken to pull or build any Docker images used in the environment, especially if these have not already been cached
* the I/O overhead introduced by Docker, particularly for volume mounts on macOS and Windows

The focus of this RFC is reducing the impact of the third factor above.

Volume mounts are primarily used in batect for two purposes:

* to make application code available within the container
* to cache files such as external libraries or packages between runs to remove the need to download them for each build or test run (and thus save time)

While the throughput of volume mounts on macOS and Windows is generally comparable to native file access within a container, the latency performing I/O operations such as opening a file handle can often be significant, as these need to cross from the Linux VM hosting Docker to the host OS and back again.

This increased latency quickly accumulates, especially when many file operations are involved. This particularly affects languages such as JavaScript and Golang that encourage distributing all dependencies as source code and breaking codebases into many small files, as even a warm build with no source code changes still requires the compiler to examine each dependency file to ensure that the cached build result is up-to-date.

Mount options such as `delegated` have improved performance somewhat, but there is still a significant difference between native I/O and I/O accessing a mount.

Furthermore, configuring and managing cache directories adds friction to developers adopting batect, especially when considering requirements such as sharing caches between successive CI build runs and performing clean builds.



# Proposed solution

To resolve the issues outlined above, this RFC proposes adding a new volume mount mechanism designed specifically for cache directories that uses Docker volumes instead of mounting directories from the host operating system.

In some situations, it is still preferable to use a mounted directory instead of a volume (eg. to persist a cache across CI builds by configuring the CI tool to archive and restore a directory), so batect will also offer the option to use mounted directories as the backing storage for a cache, configurable with a CLI option.


# Spike results

Performance tests for a non-trivial Golang app (https://github.com/batect/abacus/commit/428bab1a8a142eb41fd6062de61ac340ad58b5fe) were performed using the test scripts in this directory. Tests were performed on a 2018 MacBook Pro running macOS 10.15.3 and Docker Desktop 2.2.0.3.

|                                                                          | Using mount (no options) | Using mount (`delegated` mode) | Using volume |
| ------------------------------------------------------------------------ | ------------------------ | ------------------------------ | ------------ |
| Download dependencies (`go mod download`)                                | 44.460s                  | 25.941s                        | 11.336s      |
| Initial build with no build cache (`go build` with some options)         | 10.810s                  | 5.939s                         | 4.127s       |
| Subsequent build with no changes (`go build` with same options as above) | 6.312s                   | 1.428s                         | 0.360s       |

Downloading dependencies was run a number of times to ensure no network-level caching was impacting the results.

While this is only one highly unscientific test for one codebase, it gives an indication of the likely performance gains from the proposed solution.



# Details

## Configuration file changes

When specifying a volume mount for a container, the developer would specify that this is a cache directory with `type: cache`, for example:

```yaml
containers:
  build-env:
    build_directory: .batect/build-env
    volumes:
      # Mount code into container
      - local: .
        container: /code
        options: cached

      # Mount cache at /go
      - type: cache
        name: build-go-cache
        container: /go
```

`name` is also required and can be any valid Docker volume name. A cache can be shared by multiple containers within the same project by using the same `name`. `local` is not permitted for a cache as it would have no effect.

`options` can be specified for caches and can be any valid Docker volume mount options (eg. `ro`).

Not specifying `type` or specifying `type: host` would result in the existing behaviour of mounting a local directory into the container.


## CLI changes

Examples for running a task:

`./batect build`: run `build` task with default cache type (volumes)

`./batect --cache-type=directory build`: run `build` task with directory mounts used for caches

`./batect --cache-type=volume build`: run `build` task with volumes used for caches



New `--clean` command:

`./batect --clean`: delete all caches for default cache type (can also use `--cache-type` to override default) and then exit



## Behaviour changes

### When cache type is volume

If a container has a mount with `type: cache`, then whenever that container is started, a Docker volume is mounted into the container at the path specified by `container`. If the volume already exists, it is reused, and if it does not exist, it is created before the container is created.

To allow sharing volumes between task invocations while ensuring that caches are only shared within the same project, created Docker volumes will be suffixed with an auto-generated ID that is unique to the project. This ID will be persisted in a file named `cache-id` in the `.batect/caches` directory in the directory containing the configuration file. For example, if the ID is `abc123`, then the volume for the example configuration above would be `batect-cache-abc123-build-go-cache`.



### When cache type is directory mount

If a container has a mount with `type: cache`, then whenever that container is started, a directory on the host is mounted into the container at the path specified by `container`. If the directory already exists, it is reused, and if it does not exist, it is created before the container is created.

To allow sharing caches between task invocations while ensuring that caches are only shared within the same project, these caches are created in the `.batect/caches` directory in the directory containing the configuration file. The name of the directory within `caches` matches the name of the cache - for example, `.batect/caches/build-go-cache` for the example configuration above.


## Impacts on other features

### 'Run as current user' mode

When 'run as current user' (RACU) mode is enabled, batect instructs Docker to run the container with a custom UID and GID (these match the host OS user's UID and GID). 

However, Docker always creates and mounts volumes with the owner and group set to `root`, except if the target directory already exists in the image, in which case the directory retains the ownership information from the image (see https://github.com/moby/moby/issues/21259 and https://github.com/moby/moby/issues/2259).

Therefore, in order for the custom user to be able to read and write to the volume, the target directory must exist in the image and have the correct owner and group set. In order to simplify this for users, batect will provide a build arg when building images that contains a shell command to create and configure these directories. 

For example:

```dockerfile
FROM alpine:3.11.3

ARG batect_cache_setup_command
RUN $batect_cache_setup_command # Equivalent to RUN mkdir -p "/path/to/cache1" && chown <uid>:<gid> "/path/to/cache1" && mkdir -p "/path/to/cache2" && chown <uid>:<gid> "/path/to/cache2"
```
