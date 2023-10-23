# Problem statement

At present, [bundles](https://batect.dev/docs/concepts/includes-and-bundles) have no encapsulation: any tasks, containers or config variables they declare are global in scope. In many cases, this is desirable. For example, a bundle can expose a set of standard tasks, or expose a reusable container for a particular ecosystem. However, in some cases, this is not desirable and can lead to name conflicts or difficulty refactoring a bundle (eg. because a bundle author has no way to know if a consumer of their bundle has taken a dependency on what was intended to be an internal implementation detail).

Furthermore, creating or consuming a bundle that can be configured in different ways is not intuitive: either bundles must define a convention for discovering their configuration (eg. by having tasks read configuration from a file in a known location within the project), or define a config variable with no default that must be set outside the root project's `batect.yml` (either in a `batect.local.yml` file or with `--config-var` on the command line).



# Goals

* Make it easier to consume and configure bundles
* Make it easier to author and maintain bundles
* Minimise the risk of conflicts between bundles or the root project



# Proposed solution

## Stage 1: bundle encapsulation and configuration-on-declaration

In this stage, we introduce the concept of a *scope*. A scope is a container for declarations, similar to the concept of scopes in many programming languages.

In a project, each bundle added to a project would have its own scope, which would apply to the bundle's main configuration file and any other files included into the bundle. In addition, the root project would have its own scope, which would apply to the project's main configuration file and any other files included in the configuration.

A scope would be a container for config variables only initially - other items such as containers and tasks would continue to exist globally. Config variables would only be able to be used in [expressions](https://batect.dev/docs/reference/config/expressions) in the same scope they are declared. For example, a config variable declared in the project's main configuration file could only be referenced in that file or in other files included in the project, but not from within any bundles added to the project.

It would be valid to use the same name for config variables in different scopes. For example, `warnings_as_errors` could be defined in both the root project and a bundle, and these would be considered different variables, accessible from their respective scopes only.

To better delineate between files that are included into the scope and bundles which have their own scope, the [`include`](https://batect.dev/docs/reference/config/includes#definition) property in configuration files would only be used for file includes, with a new `bundles` property added to support file-based or Git-based bundles. The existing behaviour for `include` (including support for Git includes and the use of global scope) would be preserved for a transition period, but any use of Git includes from `include` would generate a warning and eventually be removed.

**Before:**

```yaml
include:
  - tests.yml

  - type: git
    repo: https://github.com/batect/shellcheck-bundle.git
    ref: 0.7.0

  - type: git
    repo: https://github.com/batect/hadolint-bundle.git
    ref: 0.16.0

  - bundles/my-cool-bundle/batect-bundle.yml
```

**After:**

```yaml
include:
  - tests.yml

bundles:
  - type: git
    repo: https://github.com/batect/shellcheck-bundle.git
    ref: 0.7.0

  - type: git
    repo: https://github.com/batect/hadolint-bundle.git
    ref: 0.16.0

  - type: file
    path: bundles/my-cool-bundle/batect-bundle.yml
```



To allow passing configuration to bundles, `bundles` would also allow setting values for bundles' config variables, for example:

```yaml
bundles:
  - type: git
    repo: https://github.com/batect/shellcheck-bundle.git
    ref: 0.7.0
    config:
      warnings_as_errors: true
      files: **/*.sh
```

These values could be literals or expressions. [Default values](https://batect.dev/docs/reference/config/config-variables#default) for config variables would also be extended to support expressions. Instantiating a bundle that has a config variable with no default without providing a value for that config variable would generate an error.



## Stage 2: private containers

As a separate, later improvement, bundles would be able to define private containers. Private containers would only be able to be referenced by tasks defined in the same scope (eg. the same bundle). Containers that are not private would be considered 'public'.

Containers would be defined as private by setting the `scope` property to `private`, for example:

```yaml
containers:
  build-env:
    image: golang:1.17.0
    scope: private
```

Similar to config variables, private containers in different scopes could have the same name without issue.

An open question is how to handle scenarios where there is both a public and private container with the same name. For example, consider a bundle with a private `build-env` container that references another bundle that defines a public `build-env` container: should this be possible, or should it generate an error? Generating an error is likely simplest.

On top of this, to promote better encapsulation, tasks would only be allowed to refer to containers in their scope or in bundles their scope references (including bundles added transitively). For example, consider the following configuration:

* root project: references bundles A and B
* bundle A: defines container `container-A`
* bundle B: references bundle C
* bundle C: defines container `container-C`

```
                   +----------------+
                   |                |
            +----->| bundle A       |
            |      |                |
+-----------+--+   +----+-----------+
|              |        |container A|
| root project |        +-----------+
|              |
+-----------+--+   +----------------+   +----------------+
            |      |                |   |                |
            +----->| bundle B       +-->| bundle C       |
                   |                |   |                |
                   +----------------+   +----+-----------+
                                             |container C|
                                             +-----------+
```

In this configuration, tasks in the root project could use `container-A` or `container-C`, and tasks in bundle B could use `container-C`. However, neither bundle B nor C could use `container-A`, as they have no reference to the scope it was declared in, bundle A.



# Details

## Documentation changes

Will likely need to add:

* migration guidance (including ideally some kind of automated tool or script to migrate from old `include` to new `bundle` syntax)
* explanation of the concept of scopes
* new reference page for `bundles`



Will likely need to change:

* [includes and bundles](https://batect.dev/docs/concepts/includes-and-bundles) concept page
* reference pages for [includes](https://batect.dev/docs/reference/config/includes) and [config variables](https://batect.dev/docs/reference/config/config-variables)
* examples in official bundle readmes to show new syntax ([example](https://github.com/batect/bundle-dev-bundle#usage))



## CLI changes

None expected, with the possible exception of the addition of a migration tool to convert from deprecated `include` usage to `bundle`.

`--config-var` will only support setting config variables in the root project, and `--config-var-file` will similarly only set config variables for the root project.



## Behaviour changes

In addition to what is described above, we will need to add handling for the case where an attempt is made to reference a variable that is not in scope. For example:

Variable does not exist in any scope: `The config variable 'warnings_as_errors' has not been defined.`

Variable exists in another scope: `The config variable 'warnings_as_errors' has not been defined in the current scope (the root project). It is defined in other scopes: the bundle 'https://github.com/batect/shellcheck-bundle.git' and the bundle 'bundles/my-cool-bundle/batect-bundle.yml'.`





## Impacts on other features

None expected.
