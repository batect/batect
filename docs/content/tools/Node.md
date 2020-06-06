# Node.js

You can see an example of configuring and using TypeScript and Yarn with batect in the [TypeScript sample project](https://github.com/batect/batect-sample-typescript),
and an example of using Cypress for UI testing with batect in the [Cypress sample project](https://github.com/batect/batect-sample-cypress).

## Example configuration

```yaml
containers:
  build-env:
    image: node:13.8.0
    volumes:
      - local: .
        container: /code
        options: cached
      - type: cache
        name: node_modules
        container: /code/node_modules
    working_directory: /code
    enable_init_process: true
```

## Caching dependencies

!!! tip "tl;dr"
    Mount a cache into your container for the `node_modules` directory, otherwise you'll experience poor performance on macOS and Windows.

Both NPM and Yarn download and store dependencies in the `node_modules` directory in your application's directory. However, when running on macOS and Windows,
Docker exhibits poor I/O performance for directories mounted from the macOS or Windows host, as discussed in the section on [caches](../tips/Performance.md#io-performance).

The solution to this is to mount a [cache](../tips/Performance.md#cache-volumes) that persists between builds into your container for `node_modules`.

## Issues with signals not being handled correctly

!!! tip "tl;dr"
    If signals such as `SIGINT` (which is what happens when you press Ctrl+C) aren't being handled correctly by your Node.js-based application,
    enable [`enable_init_process`](../config/Containers.md#enable_init_process) for that container

Node.js does not behave correctly when it is running as PID 1, which is what happens when running Node.js inside a container. The most noticeable issue
this causes is that applications do not respond correctly to signals such as `SIGINT` (which is generated when you press Ctrl+C).

The solution is to run another process (an 'init process') as PID 1, which then runs your application and handles and forwards signals to it.

Docker has a slimmed-down init process built in that is designed for just this scenario. You can enable it for a container in batect by setting
[`enable_init_process`](../config/Containers.md#enable_init_process) to `true`.

[This article](https://engineeringblog.yelp.com/2016/01/dumb-init-an-init-for-docker.html) has a more detailed explanation of what is happening and why
an init process solves this problem.
