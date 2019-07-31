#!/usr/bin/env bash

set -euo pipefail

REPO_SLUG=${TRAVIS_PULL_REQUEST_SLUG:-$TRAVIS_REPO_SLUG}
GIT_COMMIT=${TRAVIS_PULL_REQUEST_SHA:-$TRAVIS_COMMIT}

if [ -z "${TRAVIS_TAG:-}" ]; then
    GIT_BRANCH=${TRAVIS_PULL_REQUEST_BRANCH:-$TRAVIS_BRANCH}
else
    GIT_BRANCH=master
fi

function main() {
    URL="https://ci.appveyor.com/api/projects/$REPO_SLUG/history?recordsNumber=20&branch=$GIT_BRANCH"

    echo "Will poll $URL looking for commit $GIT_COMMIT."

    while true; do
        build=$(curl --fail --show-error --silent "$URL" | jq "[.builds | .[] | select(.commitId == \"$GIT_COMMIT\")][0]") || { sleep 10; continue; }
        id=$(echo "$build" | jq -r ".buildId")
        status=$(echo "$build" | jq -r ".status")

        if [ "$status" = "null" ]; then
            echo "Build has not started."
        else
            echo -n "https://ci.appveyor.com/project/charleskorn/batect/builds/$id: "

            case "$status" in
            success)
                echo "build succeeded."
                exit 0
                ;;
            failed)
                echo "build failed."
                exit 1
                ;;
            cancelled)
                echo "build was cancelled."
                exit 1
                ;;
            *)
                echo "build has not completed: status is $status"
                ;;
            esac
        fi

        sleep 10
    done
}

main
