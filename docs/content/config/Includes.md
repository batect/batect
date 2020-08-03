# Includes

Includes allow you to separate your configuration into multiple files or share configuration between projects.

Includes can be from files, or from a Git repository.

Git repositories designed to be used in this way are referred to as 'bundles'. The [bundles page](../Bundles.md) lists some publicly-available
bundles you can use in your own projects.

The format for included files is the same as for standard configuration files. Included files can include further files, but
cannot have a [project name](#project_name).

## Examples

### File include

If `/my-project/a.yml` contains:

```yaml
containers:
  my-container:
    image: alpine:1.2.3

include:
  - includes/b.yml
```

And `/my-project/includes/b.yml` contains:

```yaml
tasks:
  my-task:
    run:
      container: my-container
```

Then the resulting configuration is as if `/my-project/a.yml` was:

```yaml
containers:
  my-container:
    image: alpine:1.2.3

tasks:
  my-task:
    run:
      container: my-container
```

### Git include

The following configuration file includes version 0.2.0 of the [hello world bundle](https://github.com/batect/hello-world-bundle):

```yaml
include:
  - type: git
    repo: https://github.com/batect/hello-world-bundle.git
    ref: 0.2.0
```

This bundle adds a single `say-hello` task that can be run just like any other task: `./batect say-hello`.

## Configuration reference

### File includes

File includes can be specified in either of these two formats:

* Concise format:

    ```yaml
    include:
      - some-include.yml
    ```

* Expanded format:

    ```yaml
    include:
      - type: file
        path: some-include.yml
    ```

The path to the included file is relative to the directory containing the configuration file. (For example, if `/my-project/dir-a/batect.yml` has an include
for `../dir-b/extra-config.yml`, then `/my-project/dir-b/extra-config.yml` will be used.)

### Git includes

Git includes must be specified in this format:

```yaml
include:
  - type: git
    repo: https://github.com/batect/hello-world-bundle.git
    ref: 0.2.0
    path: some-bundle.yml # Optional, defaults to 'batect-bundle.yml'
```

* `repo` is the URL of the repository, as you would pass to `git clone`.

* `ref` is the name of the tag or branch, or commit hash to use.

    It's highly recommended that you use an immutable tag, as this ensures that you always get the same bundle contents each time.

* `path` is the path to the configuration file from the repository to include. It is optional and defaults to `batect-bundle.yml`.

The [bundles page](../Bundles.md) lists some publicly-available bundles you can use in your own projects.

!!! warning
    Only use bundles that you trust, as they can contain arbitrary code that will be executed when you run a task or use a container from a bundle.

## Paths in included files

Relative paths in included files such as in volume mount paths or [build directories](Containers.md#build_directory) will be resolved relative to that file's
directory (`/my-project` in `a.yml` and `/my-project/includes` in `b.yml` in [the example above](#file-include)).

Use the built-in [`batect.project_directory` config variable](ConfigVariables.md#batectproject_directory) to get the path to the root project directory
(`/my-project` in the example above), for example:

```yaml
containers:
  my-other-container:
    image: alpine:1.2.3
    volumes:
      - local: <{batect.project_directory}/scripts
        container: /code/scripts
```

## Building bundles

See the [bundles page](../Bundles.md) for tips on how to build a bundle.
