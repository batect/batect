# Travis CI

!!! tip "tl;dr"
    Use the Xenial environment, enable Docker and you're all set.

Travis CI's Xenial environment includes everything batect requires, so all that needs to be done to use batect
with Travis CI is to enable the Docker service.

Adding the following to your `.travis.yml` file selects the Xenial environment and enables Docker:

```yaml
dist: xenial

services:
  - docker
```

You can see a full example of using batect with Travis CI in [the Java sample project](https://github.com/batect/batect-sample-java).

## Caching between builds

If you're using [caches](../tips/Performance.md#cache-volumes), you can persist these between builds with the following configuration:

```yaml
dist: xenial

services:
  - docker

env:
  - BATECT_CACHE_TYPE=directory

cache:
  directories:
    - .batect/caches
```
