# Proxies, Docker and batect

!!! tip "tl;dr"
    batect will do its best to make things just work with proxies, but you'll need to configure proxies for pulling images yourself

Most applications expect to find proxy configuration in a number of environment variables. The most common are:

* `http_proxy`: proxy to use for HTTP requests
* `https_proxy`: proxy to use for HTTPS requests
* `ftp_proxy`: proxy to use for FTP requests
* `no_proxy`: comma-separated list of domains or addresses for which connections should bypass the proxy (ie.
  be direct to the destination)

There are three points where a proxy could be required during the lifecycle of a container:

1. at image pull time
2. at build time, after the image has been pulled
3. at run time

Each of these are handled slightly differently by Docker, and so batect does its best to make your life easier.

## At image pull time

When pulling an image, the Docker daemon uses any proxy-related environment variables in the Docker daemon's
environment to determine whether or not to use a proxy. These settings cannot be set at image pull time, so
batect can't configure these settings for you - you must configure them yourself.

On OS X, Docker defaults to using your system's proxy settings, and you can change these by going to the Docker icon >
Preferences > Proxies.

On Linux, you may need to configure the Docker daemon's proxy settings yourself.
[This page in the Docker documentation](https://docs.docker.com/engine/admin/systemd/#httphttps-proxy) gives an example of
how to configure a Docker daemon running with systemd.

## At build time, after the image has been pulled

After pulling the base image, all subsequent build steps use the environment variables of the build environment, which is a
combination of:

* any environment variables defined in the base image with `ENV` instructions
* any build arguments defined in the Dockerfile with `ARG` instructions
* any environment variables defined in the Dockerfile with `ENV` instructions
* any of the [pre-defined build arguments](https://docs.docker.com/engine/reference/builder/#predefined-args), if a value is
  provided for them

This last point is the most relevant to proxy settings - as `http_proxy`, `https_proxy`, `no_proxy` etc. are defined as
pre-defined build arguments, we can pass the host's proxy environment variables into the build environment as build arguments.

batect automatically propagates any proxy environment variables configured on the host as build arguments unless the `--no-proxy-vars`
flag is passed to `batect`.

Note that build arguments are not persisted in the image - they exist only as environment variables at build time. Furthermore,
the pre-defined proxy-related build arguments (unlike normal build arguments) do not impact Docker's cache invalidation logic -
so if an image build succeeded with `http_proxy` set to `http://brokenproxy`, changing `http_proxy` to `http://workingproxy` will
not cause a rebuild. (The reasoning behind this is that if the build has succeeded with one proxy, then switching to another
proxy should have no impact.)

## At run time

The set of run time environment variables is defined by:

* any environment variables defined in the image (including any base images) with `ENV` instructions
* any container-specific environment variables specified in the container or task in `batect.yml`

batect automatically propagates any proxy environment variables configured on the host as environment variables unless the
`--no-proxy-vars` flag is passed to `batect`.

Starting with v0.14, if propagating proxy environment variables is enabled, and
[any proxy environment variable recognised by batect](#proxy-environment-variables-recognised-by-batect) is set, batect will also add the names
of all containers started as part of the task to `no_proxy` and `NO_PROXY` (or create those environment variables if they're not set).
This ensures that inter-container communication is not proxied.

## Proxy environment variables recognised by batect

batect will propagate the following proxy-related environment variables:

* `http_proxy`
* `HTTP_PROXY`
* `https_proxy`
* `HTTPS_PROXY`
* `ftp_proxy`
* `FTP_PROXY`
* `no_proxy`
* `NO_PROXY`

Starting with v0.18, batect will add missing environment variables if only one in a pair is defined. (For example, if `http_proxy` is
defined, but `HTTP_PROXY` isn't, then both `http_proxy` and `HTTP_PROXY` are propagated, with `HTTP_PROXY` set to the same value as
`http_proxy`.)

## Proxies running on the host machine

If you run a local proxy on your host machine such as [Cntlm](http://cntlm.sourceforge.net/), referring to this proxy with `localhost`
will not work from inside a Docker container, as `localhost` refers to the container, not the host machine.

Starting with v0.16, if you are running batect on OS X with Docker 17.06 or later, batect will automatically rewrite proxy-related environment
variables that refer to `localhost`, `127.0.0.1` or `::1` so that they refer to the host machine.

If you are running batect on Linux, or using an older version of Docker, batect will not rewrite proxy-related environment variables.
Support for Linux will be added in the future, check [this issue on GitHub](https://github.com/charleskorn/batect/issues/10) for updates.
