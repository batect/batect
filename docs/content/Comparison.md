# Comparison with other tools

How does batect compare to...

## ...Vagrant?

Vagrant's use of virtual machines means that it is very heavyweight, making it difficult to run multiple projects'
environments at once. This is especially problematic on CI servers where we'd like to run multiple builds in parallel.

Furthermore, the long-lived nature of virtual machines means that it's very easy for a developer's machine to get out of sync
with the desired configuration, and nothing automatically re-provisions the machine when the configuration is changed - a
developer has to remember to re-run the provisioning step if the configuration changes.

## ...using shell scripts to drive Docker?

While it's certainly possible, it quickly gets unwieldy and is difficult to effectively parallelise tasks that can run in parallel.

## ...Docker Compose?

In the past, I've used Docker Compose to implement the same idea that is at the core of batect. However, using Docker Compose
for this purpose has a number of drawbacks.

In particular, Docker Compose is geared towards configuring an application and its dependencies and deploying this whole stack
to something like Docker Swarm. Its CLI is designed with this purpose in mind, making it frustrating to use day-to-day as a development
tool and necessitating the use of a higher-level script to automate its usage.

It also does not elegantly support pulling together a set of containers in different configurations (eg. integration vs journey
testing), does each operation serially (instead of parallelising operations where possible) and has
[one long-standing bug](https://github.com/docker/compose/issues/4369) that makes waiting for containers to report as healthy
difficult.
