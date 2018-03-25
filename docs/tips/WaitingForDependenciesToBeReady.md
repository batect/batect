# Waiting for dependencies to be ready

{% hint style='tip' %}
**tl;dr**: make sure your image has a health check defined, and batect will take care of the rest
{% endhint %}

When running integration or end-to-end tests, you might need to start a number of external dependencies for your application, such as databases or
fakes for external services.

However, having these dependencies just running usually isn't sufficient - they also need to be ready to respond to requests. For example, your database of choice
might take a few seconds to initialise and start accepting queries, and during this time, any requests to it will fail. So we'd like to avoid starting our tests
before we know these things are ready, otherwise they'll fail unnecessarily.

batect supports this exact requirement by taking advantage of [Docker's health check feature](https://docs.docker.com/engine/reference/builder/#healthcheck):

* If a container's image has a health check defined, batect won't start any containers that depend on it until the health check reports that it is healthy.
* If a container's image does not have a health check defined, it is treated as though it has a health check that immediately reported that it is healthy.
* If the health check fails to report that the container is healthy before the timeout period expires, batect won't start any other containers and will abort the task.

There's [a collection of sample health check scripts provided by Docker](https://github.com/docker-library/healthcheck/) you can use as inspiration, and the
[sample projects](../SampleProjects.md) use this technique extensively.
