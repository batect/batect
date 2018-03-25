# Installation

The `batect` script is designed to be committed alongside your project, and not installed globally. It will
automatically pull down the correct version of batect for your operating system.

1. Download the latest version of `batect` from the [releases page](https://github.com/charleskorn/batect/releases),
   and copy it into your project.
2. Make sure it's executable (run `chmod +x batect`).
3. Run `./batect --version` and if you see some version information, you're good to go!

Note that a JVM (version 8 or above) must be installed to use batect. (This requirement will be removed in a future release.)

Now you're ready to configure batect for your project - check out the [getting started tutorial](GettingStarted.md), dive into the 
[configuration file reference](config/Overview.md), or take a look at one of the [sample projects](SampleProjects.md).
