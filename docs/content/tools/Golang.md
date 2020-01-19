# Golang

You can see a full example of using batect with Golang in [the Golang sample project](https://github.com/batect/batect-sample-golang).

## Caching dependencies

!!! tip "tl;dr"
    Mount a directory into your container for your `GOPATH`, otherwise you'll have to download and compile your dependencies every
    time the build runs

Golang caches the source and binaries for dependencies under your [`GOPATH`](https://golang.org/doc/code.html#Workspaces). By default, this is at `$HOME/go`.
However, because batect destroys all of your containers once the task finishes, this directory is lost at the end of every task run - which means that Golang
will have to download and compile all of your dependencies again, significantly slowing down the build.

The solution to this is to mount a directory that persists between builds into your container for your `GOPATH`.

For example, the [official Golang Docker images](https://hub.docker.com/_/golang) set `GOPATH` to `/go`, so mounting the directory `.go-cache` in your project
to `/go` inside the container will allow your dependencies to be persisted across builds.
