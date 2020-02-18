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

To resolve the issues outlined above, this RFC proposes adding a new volume mount mechanism designed specifically for cache directories that uses Docker volumes instead of mounting directories from the host operating system, except when running as part of a CI build. 

When running as part of a CI build, batect will instead default to mounting a directory on the host OS so that this directory can easily be shared between builds. (It is assumed that most CI builds occur on Linux and so the overhead of this is negligible.)

The choice of cache mechanism would also be able to be overridden with a command line option.



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

`name` is also required and can be any valid Docker volume name. A cache can be shared by multiple containers  within the same project by using the same `name`. Both `local` and `options` are not permitted for a cache as these would have no effect.

Not specifying `type` or specifying `type: mount` would result in the existing behaviour of mounting a local directory into the container.



## Behaviour changes

### Detection of runtime environment

In order to determine if batect is running as part of a CI build (and so choose a default cache mechanism), batect will check for the presence of the `CI` or `BUILD_NUMBER` environment variables. If either of these are set to any value, batect will assume that the task is running as part of a CI build and default to using directory mounts for caches. 

`CI` is set by most common CI tools, with Jenkins and TeamCity not setting `CI` but instead setting `BUILD_NUMBER`.

(Libraries such as [ci-info](https://github.com/watson/ci-info/blob/2012259979fc38517f8e3fc74daff714251b554d/index.js#L52) may also serve as inspiration for this if `CI` and `BUILD_NUMBER` are not sufficient.)

### When cache type is volume

If a container has a mount with `type: cache`, then whenever that container is started, a Docker volume is mounted into the container at the path specified by `container`. If the volume already exists, it is reused, and if it does not exist, it is created before the container is created.

To allow sharing volumes between task invocations while ensuring that caches are only shared within the same project, created Docker volumes will be suffixed with an auto-generated ID that is unique to the project. This ID will be persisted in a file named `cache-id` in the `.batect/caches` directory in the directory containing the configuration file. For example, if the ID is `abc123`, then the volume for the example configuration above would be `batect-cache-abc123-build-go-cache`.



### When cache type is directory mount

If a container has a mount with `type: cache`, then whenever that container is started, a directory on the host is mounted into the container at the path specified by `container`. If the directory already exists, it is reused, and if it does not exist, it is created before the container is created.

To allow sharing caches between task invocations while ensuring that caches are only shared within the same project, these caches are created in the `.batect/caches` directory in the directory containing the configuration file. The name of the directory within `caches` matches the name of the cache - for example, `.batect/caches/build-go-cache` for the example configuration above.



## CLI changes

Examples for running a task:

`./batect build`: run `build` task with default cache type for environment (see above)

`./batect --cache-type=directory build`: run `build` task with directory mounts used for caches

`./batect --cache-type=volume build`: run `build` task with volumes used for caches



New `--clean` command:

`./batect --clean`: delete all caches for default cache type for environment (can also use `--cache-type` to override default) and then exit

