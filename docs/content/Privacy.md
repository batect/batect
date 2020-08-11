# Privacy policy

This privacy policy details the data that is collected when you interact with batect and its related services.

## General principles

As an open source project, the batect project does not have the resources to run detailed user studies to understand best how to design features or
prioritise work, and instead relies on community feedback and data. We collect data only for the purpose of understanding how batect and its related
services are used, maintaining batect and its related services, monitoring the security and performance of its related services, and improving batect
and its related services.

Data is not:

* used for advertising purposes
* sold
* shared with others, except in aggregate, anonymous form (eg. the total number of users that used batect over a 7 day period)

## Documentation site statistical information

This documentation site ([batect.dev](https://batect.dev)) collects anonymous statistical information using Google Analytics.

### What data is collected

Google Analytics collects information such as the following:

* the number of unique users that visit the site
* how often a user returns to the site
* the referrer (eg. if you clicked on a link on a Google search result page, the search term, or if you clicked on a link on a blog post, the URL of the blog post)
* the amount of time spent on each page
* the flow of pages a user visits in a single session (eg. 50% of users that view the home page go to the CLI reference page next)
* the general geographic location (country or city) of the user based on IP address
* the browser-reported language preference
* the type of device being used (eg. phone or computer)
* technical information such as the browser version and operating system version
* performance information such as page load times

Google Analytics expressly [forbids](https://support.google.com/analytics/answer/6366371?hl=en-GB&utm_id=ad) collecting
personally identifiable information. Data is always collected over HTTPS.

The following Google Analytics features are _not_ enabled:

* [Remarketing](https://support.google.com/analytics/answer/2611268?hl=en-GB&utm_id=ad)
* [Advertising Features](https://support.google.com/analytics/answer/3450482?hl=en-GB&utm_id=ad), which includes information such as age, gender
  and interests
* [User-ID](https://support.google.com/analytics/answer/3123662?hl=en-GB&utm_id=ad), which tracks users across devices
* [Google Signals](https://support.google.com/analytics/answer/7532985?hl=en-GB&utm_id=ad), which provides similar features to the above features

### How and why the data is used

This data is used to understand how users use the documentation, identify areas for improvement, identify technical issues and
measure the impact of changes to the documentation.

Data collected from Google Analytics is not combined with data from other sources to attempt to identify users.

Only the primary maintainer of batect, [Charles Korn](https://github.com/charleskorn) has access to the Google Analytics console for this site.

### How to opt-out

Google provides a [browser add-on](https://tools.google.com/dlpage/gaoptout) that blocks Google Analytics on all sites.

There are also a number of third-party add-ons that can block Google Analytics on a per-site basis.

### How to request for your data to be deleted

Send a request to [privacy@batect.dev](mailto:privacy@batect.dev). Your data will be deleted using the Google Analytics
[User Deletion API](https://developers.google.com/analytics/devguides/config/userdeletion/v3) within 14 days.

## Documentation site hosting

This documentation site ([batect.dev](https://batect.dev)) is hosted using GitHub Pages and Cloudflare. Both of these services collect data
to enable them to operate their services, and their respective privacy policies apply to this data.

## In-app update notifications

batect checks for updated versions and displays a reminder to the user if a newer version is available.

It uses the public, unauthenticated GitHub API to check for new versions. It checks for updates at most once every 36 hours.

Running `./batect --upgrade` uses the same API in the same way to deliver new versions of batect.

### What data is collected and how it is used

No personally identifiable information or credentials are sent to GitHub as part of this process, and [GitHub's privacy policy](https://github.com/site/privacy)
applies to any data it collects.

The GitHub API requires encrypted HTTPS connections.

### How to opt-out

Run batect with `--no-update-notification` to disable checking for new versions.

Note that running `./batect --upgrade` ignores `--no-update-notification` if it is set, and will always use the GitHub API to check for a new version.

## In-app telemetry

batect can collect anonymous environment, usage and performance information as it runs.

This information does not include personal or sensitive information such as the names of projects or tasks.

### What data is collected and why

| Name | Example | Rationale for collection |
|------|---------|--------------------------|
| **Environmental information** |
| batect version | `0.51.0` | Helps correlate and compare results for different versions of batect |
| OS and version | `Mac OS X 10.15.4 (x86_64)` | Helps correlate and compare results for different operating systems and OS versions and helps plan compatibility changes for different operating systems (eg. dropping support for an older version of an operating system no longer commonly used) |
| Docker version | `19.03.8` | Helps correlate and compare results for different versions of Docker and helps plan compatibility changes for different versions of Docker (eg. dropping support for an older version of Docker no longer commonly used) |
| JVM version | `Oracle Corporation Java HotSpot(TM) 64-Bit Server VM 1.8.0_162` | Helps correlate and compare results for different JVMs and helps plan compatibility changes for different JVMs (eg. dropping support for an older version of Java no longer commonly used) |
| Git version | `2.27.0` | Helps correlate and compare results for different versions of Git and helps plan compatibility changes for different versions of Git (eg. dropping support for an older version of Git no longer commonly used) |
| Shell (value of the `SHELL` environment variable) | `bash` or `fish` | Helps plan and prioritise possible future features (eg. shell tab completion) |
| Terminal type (value of the `TERM` environment variable) | `xterm-256color` | Helps plan and prioritise possible future features (eg. CLI output options) |
| Whether stdin and stdout are connected to a TTY | | Helps plan and prioritise possible future features (eg. CLI output options) |
| Whether batect believes interactivity (eg. updating text) is supported by the console | | Helps plan and prioritise possible future features (eg. CLI output options) |
| Whether batect believes it is running on a CI tool, and the name of the CI tool | `GitHub Actions`, `CircleCI` or `Jenkins` | Helps correlate and compare results for different CI tools, helps plan documentation improvements (eg. adding documentation for commonly used CI tools not yet covered in the documentation), helps plan and prioritise possible future features (eg. closer integration with CI tools) |
| Whether the wrapper script downloaded the current version of batect for this invocation | | Helps compare telemetry data with download statistics to determine roughly what proportion of users enable telemetry |
| A unique, autogenerated user ID used to correlate information across invocations (a [version 4 randomly-generated UUID](https://en.wikipedia.org/wiki/Universally_unique_identifier#Version_4_(random))) | `00001111-2222-3333-4444-555566667777` | Helps correlate results across invocations (eg. how often users batect, how quickly users upgrade from one version to another) and helps calculate usage statistics (eg. weekly active users) |
| A unique, autogenerated session ID (a [version 4 randomly-generated UUID](https://en.wikipedia.org/wiki/Universally_unique_identifier#Version_4_(random))) | `00001111-2222-3333-4444-555566667777` | Allows easy identification and deduplication of uploaded data |
| **Usage information** |
| Command | running a task, upgrading batect, displaying help | Helps segment data (eg. determining average command duration for `--upgrade`) and understand usage behaviour (eg. how often `--help` is used)
| Exit code | `0` | Helps understand whether the invocation succeeded or not
| Time the command started in UTC | `2020-08-10T16:54:00.123Z` | Provides an ordering of invocations, helps determine average time between invocations, and together with other timing values, helps understand the performance of batect
| Time the JVM started in UTC | `2020-08-10T16:54:00.102Z` | Provides an ordering of invocations, helps determine average time between invocations, and together with other timing values, helps understand the performance of batect
| Time the command finished in UTC | `2020-08-10T16:54:03.423Z` | Provides an ordering of invocations, helps determine average time between invocations, and together with other timing values, helps understand the performance of batect
| Output mode used | `simple`, `fancy`, `quiet` or `all` | Helps understand usage behaviour and helps plan and prioritise possible future features
| Whether `--no-color` is enabled | | Helps understand usage behaviour and helps plan and prioritise possible future features
| Docker connection type | Unix socket, Windows named pipe or TCP | Helps understand usage behaviour and helps plan and prioritise possible future features
| Whether `--docker-tls` or `--docker-tls-verify` is set | | Helps understand usage behaviour and helps plan and prioritise possible future features
| Whether a custom configuration file name (ie. not `batect.yml`) is being used | `false` | Helps understand usage behaviour and helps plan and prioritise possible future features
| Whether a config variables file is being used | `true` | Helps understand usage behaviour and helps plan and prioritise possible future features
| Whether the following features have been disabled: update notifications, wrapper cache cleanup, cleanup after success, cleanup after failure, proxy environment variable propagation | | Helps understand usage behaviour and helps plan and prioritise possible future features
| **Task run information** |
| Type of container being run | Windows or Linux | Helps understand usage behaviour and helps plan and prioritise possible future features
| Type of cache being used | volume or directory | Helps understand usage behaviour and helps plan and prioritise possible future features
| Total number of tasks in the project | 10 | Helps understand usage behaviour and helps plan and prioritise possible future features (eg. performance improvements for very large projects)
| Total number of containers in the project | 4 | Helps understand usage behaviour and helps plan and prioritise possible future features (eg. performance improvements for very large projects)
| Total number of configuration variables in the project | 6 | Helps understand usage behaviour and helps plan and prioritise possible future features (eg. performance improvements for very large projects)
| Total number of prerequisite tasks required to execute before executing the main task | 2 | Helps understand usage behaviour and helps plan and prioritise possible future features (eg. performance improvements for very large projects)
| Number of config variable overrides specified on the command line | 0 | Helps understand usage behaviour and helps plan and prioritise possible future features
| Number of image overrides specified on the command line | 1 | Helps understand usage behaviour and helps plan and prioritise possible future features
| Whether an existing Docker network was specified on the command line | `false` | Helps understand usage behaviour and helps plan and prioritise possible future features
| Whether prerequisites were skipped on the command line | `false` | Helps understand usage behaviour and helps plan and prioritise possible future features
| Number of additional arguments passed on the command line to the task | 2 | Helps understand usage behaviour and helps plan and prioritise possible future features
| Number of containers started for each task | 3 | Helps understand usage behaviour and helps plan and prioritise possible future features (eg. performance improvements for very large projects)
| Time taken to load the configuration for the project | 0.05 seconds | Helps understand usage and application behaviour and helps plan and prioritise possible future features (eg. performance improvements for very large projects)
| Time taken to execute each task | 3.3 seconds | Helps understand usage and application behaviour and helps plan and prioritise possible future features (eg. performance improvements for very large projects)
| Time taken to execute each step in each task (eg. pulling or building a image, creating a container, stopping a container, removing a container) | 0.4 seconds | Helps understand usage and application behaviour and helps plan and prioritise possible future features (eg. performance improvements for certain steps)
| **Exception information** |
| Type of exception | `IOException` or `ContainerCreationFailedException` | Helps understand possible bugs, helps understand the frequency with which users see certain classes of errors and hepls plan and prioritise possible future features (eg. recovering from errors automatically or suggesting recovery actions to the user)
| Stack trace | | Helps understand possible bugs, helps understand the frequency with which users see certain classes of errors and hepls plan and prioritise possible future features (eg. recovering from errors automatically or suggesting recovery actions to the user)

### What data is not collected

As a rule, any personally-identifiable or potentially sensitive information is not collected, including:

* personally-identifiable information, such as user names or email addresses
* IP addresses (IP addresses may be captured in request logs, but IP addresses are not associated with uploaded data)
* names of projects, containers, tasks or config variables
* environment variable names or values, with the exception of `TERM` and `SHELL`
* names of Docker images used
* names of files
* any part of task command lines
* exception messages, as these cannot be guaranteed to contain only non-sensitive information
* timezone information (all timestamps collected are normalised to UTC before upload)

### How data is used

Data is used in accordance with the [general principles above](#general-principles) - to understand how batect is used, to maintain batect, to monitor its
performance and reliability, and improve it.

### How data is collected and stored

Data is collected as the application runs, and then uploaded to a secure, private GCP account dedicated to storing and processing this information.

All data is transferred over HTTPS, and encrypted at rest. A variety of controls are in place to ensure that raw data is not disclosed publicly.

### Who has access to the data

Only the primary maintainer of batect, [Charles Korn](https://github.com/charleskorn) has access to this data once it is uploaded.

Aggregated, anonymous information (eg. the total number of users that used batect over a 7 day period) may be shared with others.

### How to opt-out

When batect starts for the first time, it prompts for permission to collect this information:

```
batect can collect anonymous environment, usage and performance information.
This information does not include personal or sensitive information, and is used only to help improve batect.
More information is available at https://batect.dev/Privacy.html, including details of what information is collected and a formal privacy policy.

Is it OK for batect to collect this information? (Y/n)
```

To opt-out, respond with `no`.

To opt-out after this initial run, do any of the following:

* Run [`./batect --permanently-disable-telemetry`](CLIReference.md#disable-telemetry-permanently-permanently-disable-telemetry) once, which disables telemetry
  and deletes any data that has been collected but not yet uploaded

* Run each task with [`./batect --no-telemetry <task>`](CLIReference.md#disable-telemetry-for-this-invocation-no-telemetry) (eg. `./batect --no-telemetry build`),
  which disables telemetry for that invocation of batect

* Set the `BATECT_ENABLE_TELEMETRY` environment variable to `false`

If you wish to block telemetry data uploads at the network level, block access to `api.abacus.batect.dev`. Note that the IP address of this host name
can change at any time, so it is best to block the host name, not the IP address. (Do not block access to `batect.dev`, as that will block access to this site.)

### How to request for your data to be deleted

Send a request to [privacy@batect.dev](mailto:privacy@batect.dev), and include your user ID from `~/.batect/telemetry/config.json`. Your data will be deleted
within 14 days.

## Third-party services

The batect project uses a number of third-party services such as GitHub for code hosting, Spectrum for community chat and Bintray for artifact hosting.

When you interact with these services, their respective privacy policy applies.
