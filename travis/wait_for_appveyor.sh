#!/usr/bin/env bash

set -euo pipefail

REPO_SLUG=$1
GIT_COMMIT=$2
GIT_BRANCH=$3

function main() {
    URL="https://ci.appveyor.com/api/projects/$REPO_SLUG/history?recordsNumber=20&branch=$GIT_BRANCH"

    echo "Will poll $URL."

    while true; do
        build=$(curl --fail --show-error --silent "$URL" | jq "[.builds | .[] | select(.commitId == \"$GIT_COMMIT\")][0]")
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
