#!/usr/bin/env bash

set -euo pipefail

SCRIPT_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"$SCRIPT_PATH/setup_creds.sh"
gsutil -m rsync -r build/bintray gs://batect-artifacts/$TRAVIS_BUILD_NUMBER/build/bintray
gsutil -m rsync -r build/release gs://batect-artifacts/$TRAVIS_BUILD_NUMBER/build/release
gsutil -m rsync -r docs/build/docs gs://batect-artifacts/$TRAVIS_BUILD_NUMBER/docs/build/docs
