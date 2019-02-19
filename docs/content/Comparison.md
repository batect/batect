# Comparison with other tools

!!! question "Feedback wanted"

    Are you wondering about how batect compares to other tools? Do you have your own reasons for or against batect compared
    to one of the tools mentioned below?

    Please [file an issue](https://github.com/charleskorn/batect/issues) with your questions, comments and feedback.

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

## ...CI tools with a local runner?

As an example, both GitLab CI and CircleCI have CLIs that allow you to run your build on your local machine, using the same
containers (and therefore environment) as they do when running your build on the CI server.

These tools have been designed to primarily be great CI servers, with the local CLI intended to be a convenience to allow
developers to test changes to the build configuration, rather than being a day-to-day development tool. batect, on the other hand,
was designed from the beginning to be a great day-to-day development tool that also works equally well on CI.

Specific drawbacks of these tools compared to using batect include:

* batect provides a significantly better developer experience, with a simpler, easier to use CLI, clearer and more concise output (with more details
  available when required), and clearer error messages.

    One specific example would be the experience when a dependency container fails to become
    healthy - batect will not only tell you which container did not become healthy, but also automatically display the output from the last
    health check, and also provides the option to not clean up the dependency containers if they fail to allow you to investigate further.

* batect supports using local Dockerfiles to define the images used, rather than requiring that all images be pushed to a Docker registry.
  This provides a number of benefits:

    * Additional configuration or installation of software over and above what is included in the base image can be codified in the Dockerfile,
      built once per machine that uses it and then cached, saving time over doing this additional configuration or installation at the beginning
      of each and every build.

    * This also reduces the need to bloat the base image with configuration or software required by only one or two users of the base image,
      reducing their size and improving maintainability.

    * Enabling changes to be made to the build and testing environments in the same repository as the application's code enhances traceability
      and understanding of why changes were made - the code change can form part of the same commit as the environmental change required to
      support it.

* These tools have only basic support for dependencies in other containers (for example, a database used for integration testing),
  and require the configuration of other tools such as [Dockerize](https://github.com/jwilder/dockerize) to ensure that dependencies are ready
  for use before they are used. This does not take advantage of images' built-in health checks and the benefits this mechanism has, such as
  a warm-up period.

    Furthermore, this leaves the developer to manually manage transitive dependencies between these containers, and all of the limitations with
    regard to the images used for the build environment discussed above apply equally to dependency images.

* These tools don't provide time-saving functionality such as
  [automatically configuring proxies at image build time and in the container](tips/Proxies.md).

* As these tools are designed to run the build and only the build in exactly the same way every time, they do not support passing additional
  arguments to the task, making it difficult to change options for tasks that may be helpful during development, such as enabling a debugger
  or more verbose logging.

* These tools don't support easily mounting the local working copy into the build container, which means they can't be used for tasks that
  rely on detecting changes to code, such as a continuous unit test task.

* These tools don't have a way to easily codify and share tasks not used in the build but used by developers, such as a task
  that spins up the app with stubbed dependencies.
