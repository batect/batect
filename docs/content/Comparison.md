# Comparison with other tools

!!! question "Feedback wanted"

    Are you wondering about how batect compares to other tools? Do you have your own reasons for or against batect compared
    to one of the tools mentioned below?

    Please [file an issue](https://github.com/batect/batect/issues) with your questions, comments and feedback.

How does batect compare to...

## ...using shell scripts to drive Docker?

While it's certainly possible, it quickly gets unwieldy and is difficult to effectively parallelise tasks that can run in parallel.
It is also difficult to ensure that all resources created during the task, such as containers and networks, are always correctly
cleaned up once the task completes, especially if the task fails.

## ...Docker Compose?

In the past, I've used Docker Compose to implement the same idea that is at the core of batect. However, using Docker Compose
for this purpose has a number of drawbacks.

In particular, Docker Compose is geared towards describing an application and its dependencies and starting this whole stack.
Its CLI is designed with this purpose in mind, making it frustrating to use day-to-day as a development tool and necessitating
the use of a higher-level script to automate its usage.

Furthermore, Docker Compose has no concept of tasks, further cementing the need to use a higher-level script to provide the ability
to execute different commands, run prerequisite tasks or setup commands and provide the discoverability that comes with a
[go script](https://www.thoughtworks.com/insights/blog/praise-go-script-part-i).

Docker Compose also has equivalent concept to [bundles](Bundles.md).

Docker Compose is also significantly slower than batect, as it does not parallelise all operations - in one test, batect was 17%
faster than Docker Compose.

It also does not elegantly support pulling together a set of containers in different configurations (eg. integration vs functional
testing), does not handle [proxies](tips/Proxies.md) or [file permission issues on Linux](tips/BuildArtifactsOwnedByRoot.md)
automatically and does not support [waiting for dependencies to become healthy](tips/WaitingForDependenciesToBeReady.md) as of
version 3.

## ...Dojo?

[Dojo](https://github.com/kudulab/dojo) was built with very similar goals to batect, but takes a slightly different approach.

There are a number of differences between Dojo and batect:

* Dojo requires local installation, which means different developers can be running different versions of Dojo. Batect uses a wrapper
  script committed to source control to manage the version of batect and ensure that everyone - developers and CI - use the same version and
  so have a consistent experience.

* Dojo requires Docker images to conform to [a number of requirements](https://github.com/kudulab/dojo#image-requirements-and-best-practices)
  to make the most of its features. Batect supports using any Docker image and instead requires some features to be configured in your batect
  configuration file.

* Dojo does not have built-in support for running multiple containers and instead delegates to Docker Compose to manage multiple containers,
  with many of the drawbacks described above including noticeably lower performance.

* Dojo does not support using a local Dockerfile. batect supports this as a first-class citizen, which allows developers to easily
  extend images for their needs without needing to publish them to a Docker image registry.

* Dojo has no concept of tasks and requires documentation such as a readme or a separate script to communicate these to developers.
  Batect supports tasks and prerequisites, removing the need for a separate [go script](https://www.thoughtworks.com/insights/blog/praise-go-script-part-i).
  Batect also supports the concept of [bundles](Bundles.md), making it easy to share configuration between projects and bootstrap
  projects quickly with sensible defaults.

* Dojo has very verbose and detailed default output. batect omits details that would largely be irrelevant in day-to-day development
  work by default and instead focuses on output from tasks.

* Dojo does not support Windows or Windows containers, whereas batect does.

* Dojo lacks more advanced features that batect provides to make working with Docker easier and faster, such as
  [cache volumes](tips/Performance.md#cache-volumes) and automatic configuration of [proxies](tips/Proxies.md).

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

## ...Vagrant?

Vagrant's use of virtual machines means that it is very heavyweight, making it difficult to run multiple projects'
environments at once. This is especially problematic on CI servers where we'd like to run multiple builds in parallel.

Furthermore, the long-lived nature of virtual machines means that it's very easy for a developer's machine to get out of sync
with the desired configuration, and nothing automatically re-provisions the machine when the configuration is changed - a
developer has to remember to re-run the provisioning step if the configuration changes.
