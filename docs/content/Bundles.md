# Bundles

Bundles are pre-built sets of containers and tasks you can pull into your project with [`include`](config/Includes.md).

## Available bundles

!!! warning
    Only use bundles that you trust, as they can contain arbitrary code that will be executed when you run a task or use a container from a bundle.

The following bundles are available:

* [`hello-world-bundle`](https://github.com/batect/hello-world-bundle) :octicons-check-circle-fill-16:{: .green }
  A sample bundle that demonstrates a basic development experience for creating a bundle, including an automated test setup.

:octicons-check-circle-fill-16:{: .green }: bundles built and maintained by the batect project

If you'd like to share a bundle that you've created, please feel free to submit a PR to update this page.

## Tips for building bundles

* **Consider building and publishing any Docker images your bundle uses rather than using [`build_directory`](config/Containers.md#build_directory).**

    Pulling a pre-built image is generally faster than building an image from a Dockerfile.

* **Do not store state from tasks in the bundle's working copy.**

    batect clones the repository and checks out the given tag or branch, and shares this working copy between projects to save time and disk space.
    Storing state in this working copy would mean that different projects could interfere with one another.

    Instead, store any state in the project's directory. Use the [`batect.project_directory` config variable](config/ConfigVariables.md#batectproject_directory)
    to get the path to the project's directory. The [paths in included files section in the `include` reference](config/Includes.md#paths-in-included-files) has an example.

* **Publish immutable tags.**

    Similar to Docker's behaviour when pulling image tags, batect only checks that it has previously cloned the Git reference (branch name, tag name or
    commit) - it does not check that the local clone is up-to-date with that reference. Therefore, using an immutable reference such as a tag ensures
    that everyone using the project gets the exact same version of the bundle.

    It is recommended you use [semantic versioning](https://semver.org/) when versioning bundles.

* **Test your bundle.**

    Testing your bundles ensures that they work as expected. The [hello world bundle](https://github.com/batect/hello-world-bundle) has an example
    of how to do this.
