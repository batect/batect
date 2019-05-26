# Configuration file overview

batect uses a YAML-based configuration file.

By convention, this file is called `batect.yml` and is placed in the root of your project (alongside the `batect` script).
You can, however, use a different name or location, and tell `batect` where to find it with the `-f` option.

The root of the configuration file is made up of:

## `project_name`

The name of your project. Used to label any images built.

If a project name is not provided, the project name is taken from the directory containing the configuration file. For example, if your configuration
file is `/home/alex/projects/my-cool-app/batect.yml` and you do not provide a project name, `my-cool-app` will be used automatically.

Project names must be valid Docker references:

* they must contain only:
    * lowercase letters
    * digits
    * dashes (`-`)
    * single consecutive periods (`.`)
    * one or two consecutive underscores (`_`)
* they must not start or end with dashes, periods or underscores

## `containers`

Definitions for each of the containers that make up your various environments, in `name: options` format.

Container names must be valid Docker references:

* they must contain only:
    * lowercase letters
    * digits
    * dashes (`-`)
    * single consecutive periods (`.`)
    * one or two consecutive underscores (`_`)
* they must not start or end with dashes, periods or underscores

[Detailed reference for `containers`](Containers.md)

## `tasks`

Definitions for each of your tasks, the actions you launch through batect, in `name: options` format.

[Detailed reference for `tasks`](Tasks.md)

## Anchors, aliases, extensions and merging

Available since v0.27.

batect supports YAML anchors and aliases. This allows you to specify a value in one place, and
refer to it elsewhere. For example:

```yaml
somewhere: &value-used-multiple-times the-value

# This is equivalent to somewhere-else: the-value
somewhere-else: *value-used-multiple-times
```

Anchors (`&...`) must be defined before they are referenced with an alias (`*...`).

batect also supports extensions, which behave in an identical way, but allow you to define values
before you use it for the first time. The following is equivalent to the example above:

```yaml
.value-used-multiple-times: &value-used-multiple-times the-value

somewhere: *value-used-multiple-times
somewhere-else: *value-used-multiple-times
```

Extensions must be defined at the root level of your configuration file, and the key must start
with a period (`.`).

batect also supports the merge operator (`<<`) in maps. For example:

```yaml
.common-environment: &common-environment
  ENABLE_COOL_FEATURE: true
  DATABASE_HOST: postgres:1234

tasks:
  run-app:
    run:
      ...
      environment: *common-environment # Just uses the values in common-environment as-is

  run-app-without-cool-feature:
    run:
      ...
      environment:
        << : *common-environment # Use common-environment as the basis for the environment in this task...
        ENABLE_COOL_FEATURE: false # ...but override the value of ENABLE_COOL_FEATURE
```

You can merge a single map with `<<: *other-map`, or multiple maps with `<<: [ *map-1, *map-2 ]`.

Local values take precedence over values merged into a map (regardless of the position of the `<<` entry),
and values from sources earlier in the list of maps take precedence over values from later sources.
(For example, if both `map-1` and `map-2` define a value for `PORT` in the example earlier, the
value in `map-1` is used.)

## Examples

Examples are provided in the reference for [`containers`](Containers.md#examples) and [`tasks`](Tasks.md#examples).

For further examples and real-world scenarios, take a look at the [sample projects](../SampleProjects.md).
