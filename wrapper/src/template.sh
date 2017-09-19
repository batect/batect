#!/usr/bin/env bash

set -euo pipefail

VERSION="version-goes-here"
DOWNLOAD_URL=${BATECT_DOWNLOAD_URL:-"download-url-goes-here"}

ROOT_CACHE_DIR=${BATECT_CACHE_DIR:-"~/.batect/cache"}
CACHE_DIR="$ROOT_CACHE_DIR/$VERSION"
JAR_PATH="$CACHE_DIR/batect-$VERSION.jar"

function main() {
    if ! haveVersionCachedLocally; then
        download
    fi

    runApplication "$@"
}

function haveVersionCachedLocally() {
    [ -f "$JAR_PATH" ]
}

function download() {
    echo "Downloading batect version $VERSION from $DOWNLOAD_URL..."

    if ! hash curl 2>/dev/null; then
        echo "curl is not installed or not on your PATH. Please install it and try again." >&2
        exit -1
    fi

    mkdir -p "$CACHE_DIR"
    curl -# --fail --silent --show-error --output "$JAR_PATH" "$DOWNLOAD_URL"
}

function runApplication() {
    java -jar "$JAR_PATH" "$@"
}

main "$@"
