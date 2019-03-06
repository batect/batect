#! /usr/bin/env sh

set -euo pipefail

mkdocs build

# Work around the fact that we run the build from within a Docker container (without using batect)
chmod -R a+rwx build/docs
