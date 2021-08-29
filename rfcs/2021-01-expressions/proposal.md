# Problem statement

At present, [expressions](https://batect.dev/docs/reference/config/expressions) are limited in a number of dimensions.

Firstly, they are limited in where they can be used - they can only be used in:

- [`build_args`](https://batect.dev/docs/reference/config/containers#build_args) on containers
- [`build_directory`](https://batect.dev/docs/reference/config/containers#build_directory) on containers
- `environment` on [containers](https://batect.dev/docs/reference/config/containers#environment), [tasks](https://batect.dev/docs/reference/config/tasks#environment) and [customisations](https://batect.dev/docs/reference/config/tasks#environment-1)
- the local path in [volume mounts](https://batect.dev/docs/reference/config/containers#volumes) on containers

(sample request for using expressions in more places: [#974](https://github.com/batect/batect/issues/974))

Secondly, they are limited to direct references to environment variables or config variables. This prevents more complex scenarios (eg. [falling back to another variable if one is not set](https://github.com/batect/batect/issues/872)).

Finally, the syntax is confusing. Environment variables use `$NAME`, `${NAME}` or `${NAME:-default}` and config variables only support `<NAME` or `<{NAME}` (with no default fallback), with the asymmetry making it difficult for users to understand. It's also not immediately clear whether an expression like `$NAME` will be evaluated in the context of the host machine or the container, as the behaviour varies depending on which field the expression appears in.



# Goals

* Make the existing expression language easier to understand
* Allow the use of expressions in more places throughout a configuration file - ideally in every field
* Provide an easy migration path to upgrade configuration files to the new syntax
* Lay the groundwork for extension and enhancement of the expression language in the future



## Non-goals

* Provide sophisticated functionality or programmability: this should live within tasks, not in their configuration
* Provide an extensive library of functions



# Proposed solution

There are two main parts to this proposal:

* a new syntax for all expressions
* enabling use of expressions in almost every field



## New expression syntax

The new syntax uses `${{ ... }}` to enclose an expression, with the expression appearing between the `${{` and `}}`.

This clearly disambiguates it from environment variable references in commands (`$NAME` or `${NAME}`), and is similar to other tools.

`$` is also not a special character in YAML, allowing it to be used unquoted in field values. For example, `property: ${{ ... }}` is valid YAML, whereas using a character such as `&` would require quoting like `property: "&{{ ... }}"` to prevent `&` being treated as an anchor. This similarly affects many other special characters that could be used to mark the start of an expression.

The remainder of the syntax is best explained with examples:

| Description                                                  | Old syntax                    | New syntax                                                   | Notes                                                        |
| ------------------------------------------------------------ | ----------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| Reference to environment variable                            | `$NAME` or `${NAME}`          | `${{ env.NAME }}`                                            | If `NAME` is not set on the host machine, the expression fails with an error. |
| Reference to environment variable with default               | `${NAME:-defaultname}`        | `${{ get_or_default(env.NAME, "defaultname") }}`             | If `NAME` is not set on the host machine, the literal value `defaultname` is used. |
| Reference to environment variable with default from other environment variable | Not supported                 | `${{ get_or_default(env.NAME, env.FALLBACK_NAME) }}`         | The second parameter to `get_or_default` can be any valid expression, including nested calls to `get_or_default` (or any other function). |
| Literal value within expression                              | Not applicable                | `${{ "some value" }}` or `${{ "They said: \"hello!\"" }}`    | Literals within expressions are enclosed in double-quotes. Double quotes within a literal can be escaped with a backslash. |
| Concatenation with literal                                   | `Hello $NAME`                 | `Hello ${{ env.NAME }}` (concatenate literal with expression) or `${{ "Hello " + env.NAME }}` (concatenate literal inside expression) |                                                              |
| Reference to config variable                                 | `<NAME` or `<{NAME}`          | `${{ var.NAME }}`                                            | If `NAME` is not set, the expression fails with an error.    |
| Reference to config variable with default                    | Not supported                 | `${{ get_or_default(var.NAME, "defaultname") }}`             | If `NAME` is not set, the literal value `defaultname` is used. |
| Reference to built-in parameter                              | `<{batect.project_directory}` | `${{ batect.project_directory }}`                            |                                                              |
| Reference to built-in parameter with default                 | Not supported                 | `${{ get_or_default(batect.project_directory, "/default/dir") }}` | Fails with an error: `'batect.project_directory' always has a value and so cannot be used with 'get_or_default'` |



## Expressions everywhere

With this new syntax, expressions are now unambiguous and able to easily be applied almost anywhere.

The only places that cannot support expressions are:

* task, container and config variable names and descriptions
* task prerequisites
* task or container dependencies
* project names
* task or container environment variable names (values can continue to contain expressions)
* include configuration (eg. `type`, `path`, `repo` or `ref`)





# Details

## Documentation changes

Will likely need to add:

* migration guidance (including ideally some kind of automated tool or script to migrate from old syntax to new syntax in affected fields)
* reference for new expression syntax



## CLI changes

`--config-var` and `--config-vars-file` will continue to behave as before.

If possible, we would ideally add some kind of automated migration tool to convert configuration files from the old syntax to the new syntax. For example: `./batect --migrate` or `./batect --migrate --config-file my-other-file.yml`.



## Behaviour changes

Using the old expression syntax would generate a warning with a suggestion for how to replace the deprecated syntax with the new syntax. For example:

```
/projects/service/batect.yml (line 10, column 5): warning: expression '${NAME:-default}' uses the deprecated expression syntax which will be removed in a future release of Batect. Replace this expression with '${{ get_or_default(env.NAME, "default") }}'.
```



## Impacts on other features

None expected.
