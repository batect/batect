#!/usr/bin/env bash

set -euo pipefail

SOURCE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_DIR="$( cd "$SOURCE_DIR/.." && pwd )"
IMAGE_NAME="batect-wrapper-test-env"

OUTPUT="$(docker build -t "$IMAGE_NAME" "$SOURCE_DIR/test-env" 2>&1)" || { echo "Test environment build failed:"; echo "$OUTPUT"; exit 1; }

docker run --rm -t \
    -v "$PROJECT_DIR:$PROJECT_DIR" \
    -w "$PROJECT_DIR" \
    "$IMAGE_NAME" \
    ./test/tests.py
