#!/usr/bin/env bash

set -euo pipefail

REPO_SLUG=$1
GIT_COMMIT=$2
GIT_BRANCH=$3

function main() {
    URL="https://ci.appveyor.com/api/projects/$REPO_SLUG/history?recordsNumber=20&branch=$GIT_BRANCH"

    echo "Will poll $URL."

    while true; do
        status=$(curl --fail --show-error --silent "$URL" | jq -r "[.builds | .[] | select(.commitId == \"$GIT_COMMIT\").status][0]")

        case "$status" in
        success)
            echo "Build succeeded."
            exit 0
            ;;
        failed)
            echo "Build failed."
            exit 1
            ;;
        "")
            echo "Build has not started."
            ;;
        *)
            echo "Build has not completed: status is $status"
            ;;
        esac

        sleep 10
    done
}

main
