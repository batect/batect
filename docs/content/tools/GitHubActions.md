# GitHub Actions

!!! tip "tl;dr"
    Use the `ubuntu-18.04` runner.

[GitHub Actions' Ubuntu 18.04 runners](https://help.github.com/en/actions/automating-your-workflow-with-github-actions/software-installed-on-github-hosted-runners#ubuntu-1804-lts)
come pre-installed with everything needed to run batect.

To use the Ubuntu 18.04 runner, specify `runs-on: ubuntu:18.04` in your configuration file. For example:

```yaml
jobs:
  build:
    name: "Build"
    runs-on: ubuntu-18.04
    steps:
      - uses: actions/checkout@v1
      - name: Build application
        run: ./batect build
```

## Caching between builds

If you're using [caches](../tips/Performance.md#cache-volumes), you can persist these between builds with the following configuration:

```yaml
jobs:
  build:
    name: "Build"
    runs-on: ubuntu-18.04
    env:
      BATECT_CACHE_TYPE: directory

    steps:
      - uses: actions/checkout@v1

      - name: Cache dependencies
        uses: actions/cache@v1
        with:
          path: .batect/caches
          key: batect-caches-${{ hashFiles('path to a file that uniquely identifies the contents of the caches') }}

      - # ...other build steps
```

The `key` should be a value that changes when the contents of the cache change, and remains constant otherwise. A good candidate is the hash of a dependency lockfile,
such as `Gemfile.lock`, `package-lock.json`, `yarn.lock` or `go.sum`. The
[documentation for caching](https://help.github.com/en/actions/configuring-and-managing-workflows/caching-dependencies-to-speed-up-workflows#using-the-cache-action) has
more details on `key`.
