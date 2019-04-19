# Node.js

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
