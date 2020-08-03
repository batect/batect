#! /usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PROJECTS=(
    batect-sample-cypress
    batect-sample-golang
    batect-sample-java
    batect-sample-ruby
    batect-sample-seq
    batect-sample-typescript
    golang-bundle
    hadolint-bundle
    hello-world-bundle
    java-bundle
    node-bundle
    shellcheck-bundle
)

function getLatestVersion {
    curl --fail --silent --show-error https://api.github.com/repos/batect/batect/releases/latest | jq -r '.name'
}

function updateProject {
    project_name=$1
    commit_message="Update batect to $2."
    project_dir="$(cd "$SCRIPT_DIR/../../$project_name" && pwd)"

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

        if output=$(git status --porcelain) && [ ! -z "$output" ]; then
            git commit -m "$commit_message"
            git push
        else
            echo "$project_dir is already up-to-date."
        fi
    }
}

function main {
    echo "Getting latest version info..."
    latestVersion=$(getLatestVersion)
    echo "Latest version is $latestVersion."
    echo

    for project in "${PROJECTS[@]}"; do
        updateProject "$project" "$latestVersion"
        echo
    done
}

main
