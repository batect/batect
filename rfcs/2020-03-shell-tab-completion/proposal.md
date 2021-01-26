# Problem statement

All modern shells (Bash, zsh, Fish and even PowerShell) support tab completion for an application's arguments. This saves users typing (eg. `git stat<tab>` completes to `git status`) and helps them find  the correct argument without having to wait for the application to run and fail (eg. is it `unitTest`, `unitTests` or `unit-test`?).

batect does not currently support tab completion in any shell, and this has been requested by a number of users (see https://github.com/batect/batect/issues/116 and https://github.com/batect/batect/issues/466) and has been on the [roadmap](https://github.com/batect/batect/blob/main/ROADMAP.md) [since forever](https://github.com/batect/batect/commit/fce1890a444e8a963af3767aaa181dda14d7041f).

batect's non-global installation presents some challenges to standard ways of providing tab completion, as a user could use different versions of batect (with different available command line options) simultaneously on different projects.



# Goals

* Provide tab completion for the most common shells (Bash, zsh and Fish according to telemetry) first
* Support the most common operating system (macOS according to telemetry) first
* Support simultaneous use of different batect versions in different projects at the same time
* Use existing distribution mechanisms (eg. Homebrew, [fisher](https://github.com/jorgebucaran/fisher), [oh-my-zsh](https://ohmyz.sh/)) for managing any globally-installed components
* Minimise the number and frequency of change of any globally-installed components - users should not need to update any globally-installed components every time a new batect version is released
* Support completion of both command line options (eg. `./batect --outp<tab>` to `./batect --output`) and task names (eg. `./batect b<tab>` to `./batect build`)
* Provide completions in a performant way (<200 ms on average)



# Proposed solution

There are three main parts to the proposed solution:

* a globally-installed completion script for each supported shell, distributed through Homebrew or other package managers
* a version-specific completion script for each supported shell, which is retrieved, cached and called by the global completion script
* a mechanism for the version-specific completion script to get and cache the list of tasks for a project in a performant way

Splitting the completion script in this way allows for new versions of batect to be easily supported without requiring any further installation from the user.

Caching of the version-specific completion script and each project's list of tasks is critical to achieving a satisfactory user experience, as invoking batect itself is too slow to meet the performance goal above. (According to telemetry, simply starting the JVM on macOS or Linux takes 400 ms at the 90th percentile, and it's significantly worse on Windows.)





# Details

## Documentation changes

Documentation on how to install the completion script for each supported shell will be required.



## CLI changes

No user-visible changes.

Two hidden CLI commands will be added:

* `./batect --generate-completion-script=fish`: retrieve the version-specific completion script
* `./batect --generation-completion-task-list`: retrieve the list of tasks and absolute path to all configuration files involved in the project. The list of absolute paths involved in the project is required to allow the version-specific completion script to know when a particular project's configuration has changed and re-run `./batect --generation-completion-task-list` to refresh its cache.

Hidden CLI commands aren't shown in the output of `./batect --help`.



## Behaviour changes

The globally-installed completion script will rely on searching the project's `batect` wrapper script for a line that looks like `VERSION="xxx"` to determine what version of batect the project is using and use this as a key to store a cached version of the completion script.



## Impacts on other features

None anticipated.
