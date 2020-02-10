#! /usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

function getLatestVersion {
    curl --fail --silent --show-error https://api.github.com/repos/batect/batect/releases/latest | jq -r '.name'
}

function updateProject {
    project_name=$1
    commit_message="Update batect to $2."
    project_dir="$SCRIPT_DIR/../../$project_name"

    {
        echo "Updating $project_name..."
        cd "$project_dir"

        if output=$(git status --porcelain) && [ ! -z "$output" ]; then
            echo "Error: the working copy in $project_dir is dirty."
            exit 1
        fi

        git pull --ff-only
        ./batect --upgrade
        git add batect
        git add batect.cmd
        git commit -m "$commit_message"
        git push
    }
}

function main {
    echo "Getting latest version info..."
    latestVersion=$(getLatestVersion)
    echo "Latest version is $latestVersion."
    echo

    updateProject "batect-sample-java" "$latestVersion"
    echo
    updateProject "batect-sample-ruby" "$latestVersion"
    echo
    updateProject "batect-sample-golang" "$latestVersion"
    echo
    updateProject "batect-sample-cypress" "$latestVersion"
}

main
