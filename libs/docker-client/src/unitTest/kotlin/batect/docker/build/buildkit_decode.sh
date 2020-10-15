#! /usr/bin/env bash

set -euo pipefail

# Run this script like this:
# cat <file with raw JSON events from daemon> | ./buildkit_decode.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROTOS_DIR="$(cd "$SCRIPT_DIR/../../../../../../build/protos" && pwd)"

LINE=0

while read -r event || [ -n "$event" ]; do
    ((LINE=LINE+1))
    echo "$(tput setaf 4)Line $LINE$(tput sgr0)"
    AUX=$(echo "$event" | jq 'select(.id == "moby.buildkit.trace")' | jq -r '.aux')

    if [[ "$AUX" == "" ]]; then
        echo "Line is not a trace event."
        echo "$event"
    else
        echo "$AUX" | base64 --decode | protoc --decode moby.buildkit.v1.StatusResponse --proto_path "$PROTOS_DIR" github.com/moby/buildkit/api/services/control/control.proto
    fi

    echo
done
