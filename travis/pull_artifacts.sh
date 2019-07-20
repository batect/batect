#!/usr/bin/env bash

set -euo pipefail

SCRIPT_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"$SCRIPT_PATH/setup_creds.sh"
mkdir -p build/bintray build/release docs/build/docs
gsutil -m rsync -r gs://batect-artifacts/$TRAVIS_BUILD_NUMBER/build/bintray build/bintray
gsutil -m rsync -r gs://batect-artifacts/$TRAVIS_BUILD_NUMBER/build/release build/release
gsutil -m rsync -r gs://batect-artifacts/$TRAVIS_BUILD_NUMBER/docs/build/docs docs/build/docs
