#! /usr/bin/env bash

set -euo pipefail

# Run this script like this:
# cat <file with raw JSON events from daemon> | ./buildkit_decode.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROTOS_DIR="$(cd "$SCRIPT_DIR/../../../../../../build/protos" && pwd)"

EVENTS=$(jq 'select(.id == "moby.buildkit.trace")' | jq -r '.aux')

for event in $EVENTS; do
    echo "$event" | base64 --decode | protoc --decode moby.buildkit.v1.StatusResponse --proto_path "$PROTOS_DIR" github.com/moby/buildkit/api/services/control/control.proto
    echo
done
