# Configuration file overview

batect uses a YAML-based configuration file.

By convention, this file is called `batect.yml` and is placed in the root of your project (alongside the `batect` script).
You can, however, use a different name or location, and tell `batect` where to find it with the `-f` option.

The root of the configuration file is made up of:

## `project_name` 

The name of your project. Used to label any images built.

If a project name is not provided, the project name is taken from the directory containing the configuration file. For example, if your configuration
file is `/home/alex/projects/my-cool-app/batect.yml` and you do not provide a project name, `my-cool-app` will be used automatically.

## `containers`

Definitions for each of the containers that make up your various environments. 

[Detailed reference for `containers`](Containers.md)

## `tasks`

Definitions for each of your tasks, the actions you launch through batect.

[Detailed reference for `tasks`](Tasks.md)

## Examples

Examples are provided in the reference for [`containers`](Containers.md) and [`tasks`](Tasks.md).

For further examples and real-world scenarios, take a look at the [sample projects](../SampleProjects.md).
