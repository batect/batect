# CircleCI

!!! tip "tl;dr"
    Use a machine executor with an image that has a recent version of Docker.

CircleCI's recent machine executor images include everything batect requires, so all that needs to be done to use batect
with CircleCI is to configure it to use one of those images.

A list of available images is published in the CircleCI documentation [here](https://circleci.com/docs/2.0/configuration-reference/#machine).
batect requires an image with a compatible version of Docker - currently version 18.03.1 or newer.

Adding the following to your `.circleci/config.yml` file instructs CircleCI to use a machine executor with the 201808-01 image,
which contains Docker 18.06:

```yaml
version: 2

jobs:
  build:
    machine:
      enabled: true
      image: circleci/classic:201808-01
    steps:
      - checkout
      - run: ./batect ...
```

You can see a full example of using batect with CircleCI in
[the Golang sample project](https://github.com/batect/batect-sample-golang).

## Caching between builds

If you're using [caches](../tips/Performance.md#cache-volumes), you can persist these between builds with the following configuration:

```yaml
version: 2

jobs:
  build:
    machine:
      enabled: true
      image: circleci/classic:201808-01
    environment:
      BATECT_CACHE_TYPE: directory
    steps:
      - checkout
      - restore_cache:
          key: batect-caches-{{ arch }}-{{ checksum "path to a file that uniquely identifies the contents of the caches" }}
      - # ...other build steps
      - save_cache:
          key: batect-caches-{{ arch }}-{{ checksum "path to a file that uniquely identifies the contents of the caches" }}
          paths:
            - .batect/caches
```

The `key` should be a value that changes when the contents of the cache change, and remains constant otherwise. A good candidate is the hash of a dependency lockfile,
such as `Gemfile.lock`, `package-lock.json`, `yarn.lock` or `go.sum`. The [documentation for caching](https://circleci.com/docs/2.0/caching/) has
more details on `key`.

## Simplifying configuration with CircleCI

CircleCI supports [defining reusable commands](https://circleci.com/docs/2.0/reusing-config/#authoring-reusable-commands) within your configuration file.
This can be useful if you find yourself running the same series of commands over and over again. For example, if you have a series of jobs, each of which
checks out your code, restores the cache, runs a batect task and then saves the cache, you can define a command that contains all of these steps:

```yaml
commands:
  batect:
    description: "Run a task in batect"
    parameters:
      task:
        type: string
    steps:
      - checkout
      - restore_cache:
          key: myproject-{{ checksum "yarn.lock" }}
      - run:
          command: ./batect << parameters.task >>
      - save_cache:
          key: myproject-{{ checksum "yarn.lock" }}
          paths:
            - node_modules/
```

...and then reuse it in each job:

```yaml
  build:
    machine: true
    image: circleci/classic:201808-01
    steps:
      - batect:
          task: build

  test:
    machine: true
    image: circleci/classic:201808-01
    steps:
      - batect:
          task: test
```
