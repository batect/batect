# Expressions

Some fields support expressions - references to environment variables on the host or [config variables](ConfigVariables.md).

Expressions are supported in:

* [`build_args`](Containers.md#build_args) on containers
* [`build_directory`](Containers.md#build_directory) on containers
* `environment` on [containers](Containers.md#environment), [tasks](Tasks.md#environment) and [customisations](Tasks.md#environment_1)
* the local path in [volume mounts](Containers.md#volumes) on containers

## Environment variables

You can pass environment variables from the host (ie. where you run batect) to the container by using any of the following formats:

* `$name` or `${name}`: use the value of `name` from the host as the value inside the container.

    If `name` is not set on the host, batect will show an error message and not start the task.

* `${name:-default}`: use the value of `name` from the host as the value inside the container.

    If `name` is not set on the host, `default` is used instead.

    `default` can be empty, so `${name:-}` will use the value of `name` from the host if it is
    set, or a blank value if it is not set.

    `default` is treated as a literal, it cannot be a reference to another variable.

For example, to refer to the value of the `MY_PASSWORD` environment variable on the host, use `$MY_PASSWORD` or
`${MY_PASSWORD}`. Or, to default to `insecure` if `MY_PASSWORD` is not set, use `${MY_PASSWORD:-insecure}`.

## Config variables

You can refer to the value of a [config variable](ConfigVariables.md) with `<name` or `<{name}`.
Default values for config variables can be specified with [`default`](ConfigVariables.md#default) when defining them.

## Notes

When given without braces, `name` can only contain letters, numbers and underscores.
Any other characters are treated as literals (eg. `$MY_VAR, 2, 3` with `MY_VAR` set to `1` results
in `1, 2, 3`).

When given with braces, `name` can contain any character except a closing brace (`}`) or colon (`:`).

Combining expressions and literal values is supported (eg. `My password is $MY_PASSWORD` or `<{SERVER}:8080`).

In fields that support expressions, you can escape `$` and `<` with a backslash (`\`).
