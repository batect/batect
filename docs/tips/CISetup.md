# CI setup

{% hint style='tip' %}
**tl;dr**: set up a Cron job to run `docker image prune -f` regularly on CI agents 
{% endhint %}

If you are using Dockerfiles to define your containers (as opposed to taking a pre-existing image), this can generate a
large number of orphaned images (and their associated image layers) over time. While batect goes to great lengths to
ensure that containers and networks are cleaned up after every task run, it can't know which images are unused and so
it can't safely automatically remove unused images.

These orphaned images take up disk space, and, if left unattended, can lead to exhausting all the available disk space.
This is especially a problem on CI, where a human might not notice this issue until the disk is full.

Therefore, it's recommended that CI servers running batect-based builds have a regular task that removes orphaned images.
Docker has a built-in command to do this: `docker image prune -f` (the `-f` disables the confirmation prompt). The exact
frequency will depend on your usage pattern, but once a day is usually more than sufficient.
