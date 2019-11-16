# Quick start

The `batect` and `batect.cmd` scripts are designed to be committed alongside your project, and not installed globally. They will
automatically pull down the correct version of batect for your operating system.

1. Download the latest version of `batect` and `batect.cmd` from the [releases page](https://github.com/charleskorn/batect/releases),
   and copy them into your project.

    Note that you only need the scripts - you don't need to download `batect.jar`.

2. If you're on Linux or OS X, make sure the script is executable: run `chmod +x batect`.
3. Run `./batect --version` and if you see some version information, you're good to go!

Note that a JVM (version 8 or above) must be installed to use batect. (This requirement will be removed in a future release.)

batect is compatible with Docker 17.12 and higher.

Now you're ready to configure batect for your project - check out the [getting started tutorial](GettingStarted.md), dive into the
[configuration file reference](config/Overview.md), or take a look at one of the [sample projects](SampleProjects.md).
