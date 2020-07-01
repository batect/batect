# Quick start

The `batect` and `batect.cmd` scripts are designed to be committed alongside your project, and not installed globally. Committing
them alongside your code improves consistency within your team, as everyone uses the same version of batect. They will
automatically pull down the correct version of batect for your operating system.

1. Download the latest version of `batect` and `batect.cmd` from the [releases page](https://github.com/batect/batect/releases),
   and copy them into your project.

    Note that you only need the scripts - you don't need to download `batect.jar`.

2. If you're on Linux or macOS, make sure the script is executable: run `chmod +x batect`.

3. Create your `batect.yml` to define your tasks and the environments they run in:
    * Take a look at the [sample projects](SampleProjects.md) for inspiration
    * Dive straight into [the configuration file reference](config/Overview.md)
    * Follow the [getting started tutorial](GettingStarted.md)
    * Or, if you're using another tool already and want to switch to batect,
      [batectify](https://batectify.enchanting.dev/) by [@ineffyble](https://github.com/ineffyble) can convert files from
      other tools to batect's format

## Requirements

batect requires Docker 18.03.1 or newer, Java 8 or newer (although this requirement will be removed before v1.0), and:

* On Linux and macOS: Bash and `curl`
* On Windows: Windows 10

batect supports both Linux and Windows containers.

A 64-bit version of Java is required on Windows.
