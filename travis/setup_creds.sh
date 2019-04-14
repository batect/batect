#!/usr/bin/env bash

set -euo pipefail

if [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
    echo "This is a pull request build, not setting up credentials."
    exit 0
fi

if [ "$TRAVIS_REPO_SLUG" != "charleskorn/batect" ]; then
    echo "This is a fork build, not setting up credentials."
    exit 0
fi

sudo pip install gsutil
mkdir -p ~/.creds
openssl aes-256-cbc -K $encrypted_2210a3652f18_key -iv $encrypted_2210a3652f18_iv -in travis/travis-ci.json.enc -out ~/.creds/travis-ci.json -d
echo "[Credentials]" > ~/.boto
echo "gs_service_key_file = $HOME/.creds/travis-ci.json" >> ~/.boto
