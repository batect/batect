# Config variables

Config variables allow you simplify your configuration file, and document and codify the different options available to a developer using your tasks.

They are useful for a number of use cases:

* Reducing duplication in configuration files
* Simplifying management of developer-specific preferences (eg. a developer's preferred log output level)
* Simplifying management of sets of environment-specific settings (eg. managing sets of test environment connection settings for a CI server)

Config variables can currently be used in environment variables for [containers](Containers.md#environment) and [tasks](Tasks.md#run), and [build args](Containers.md#build_args) for containers.

Config variables were introduced in v0.40.

## Values

Values for config variables are taken from the following sources, with higher values taking precedence:

* a value provided on the command line with [`--config-var <name>=<value>`](../CLIReference.md#set-a-config-variable-config-var)
* a value specified in a config variables file, either the default `batect.local.yml` or specified with [`--config-vars-file`](../CLIReference.md#set-config-variables-from-a-file-config-vars-file)
* the default value

If a variable is referenced but no value is available for it, an error occurs.

## Definition

Each config variable definition is made up of:

### `description`

A human-readable description of the variable.

### `default`

The default value of the variable.

## Examples

### Config variable with no description or default value

```yaml
config_variables:
  log_level: {}
```

`{}` is the YAML syntax for an empty object.

### Config variable with description and default value

```yaml
config_variables:
  log_level:
    description: Log level to use for application
    default: debug
```
