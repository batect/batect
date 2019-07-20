#!/usr/bin/env bash

set -euo pipefail

mkdir -p build/bintray build/release docs/build/docs
gsutil -m rsync -r gs://batect-artifacts/$TRAVIS_BUILD_NUMBER/build/bintray build/bintray
gsutil -m rsync -r gs://batect-artifacts/$TRAVIS_BUILD_NUMBER/build/release build/release
gsutil -m rsync -r gs://batect-artifacts/$TRAVIS_BUILD_NUMBER/docs/build/docs docs/build/docs
