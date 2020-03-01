# Gradle

You can see an example of configuring and using Java and Gradle with batect in the [Java sample project](https://github.com/batect/batect-sample-java).

## Example configuration

```yaml
containers:
  build-env:
    image: openjdk:13.0.2-jdk
    volumes:
      - local: .
        container: /code
        options: cached
      - type: cache
        name: gradle-cache
        container: /root/.gradle
    working_directory: /code
    environment:
      GRADLE_OPTS: -Dorg.gradle.daemon=false
```

## Caching dependencies

!!! tip "tl;dr"
    Mount a cache as the `~/.gradle` directory within the container, otherwise you'll have to download your dependencies every time the build
    runs

By default, Gradle downloads all of your application's dependencies to the `~/.gradle` directory. However, because batect destroys all of your containers once
the task finishes, this directory is lost at the end of every task run - which means that Gradle will have to download all of your dependencies again,
significantly slowing down the build.

The solution to this is to mount a [cache](../tips/Performance.md#cache-volumes) that persists between builds into the container at `~/.gradle`, so that these
downloaded dependencies are persisted between builds.

Note that you can't use `~` in the container path for a volume mount:

* If you're using [run as current user mode](../tips/BuildArtifactsOwnedByRoot.md), use the home directory you specified for [`home_directory`](../config/Containers.md#run_as_current_user).
* If you're not using [run as current user mode](../tips/BuildArtifactsOwnedByRoot.md), use `/root` as the home directory, as the vast majority of containers
  default to the root user and use this as the root user's home directory.

!!! warning
    With this configuration, you will not be able to run more than one task at a time. This is due to [a known issue with Gradle](https://github.com/gradle/gradle/issues/851).

## Disabling the Gradle daemon

!!! tip "tl;dr"
    Set the environment variable `GRADLE_OPTS` to `-Dorg.gradle.daemon=false`

When Gradle starts, it has to load itself and then compile and load your build script so that it can execute it. This can take a noticeable amount of time for
larger projects, so, by default, it starts a daemon that remains running and ready to start your build without having to load or compile anything.

However, when Gradle is running inside an ephemeral container like the ones created by batect, this daemon is pointless - it will be terminated alongside the
rest of the container at the end of the build. In fact, the cost of starting the daemon means that this is actually counter-productive, because we'll pay the
performance penalty of starting the daemon every time when we won't then benefit from it in later builds.

Therefore, it's best to disable the daemon when running Gradle inside a container. This can be done by setting the `GRADLE_OPTS` environment variable to
`-Dorg.gradle.daemon=false`.
