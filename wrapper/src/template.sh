#!/usr/bin/env bash

{
    set -euo pipefail

    VERSION="VERSION-GOES-HERE"
    DOWNLOAD_URL=${BATECT_DOWNLOAD_URL:-"DOWNLOAD-URL-GOES-HERE"}

    ROOT_CACHE_DIR=${BATECT_CACHE_DIR:-"$HOME/.batect/cache"}
    CACHE_DIR="$ROOT_CACHE_DIR/$VERSION"
    JAR_PATH="$CACHE_DIR/batect-$VERSION.jar"

    SCRIPT_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"

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
        temp_file=$(mktemp)
        curl -# --fail --show-error --location --output "$temp_file" "$DOWNLOAD_URL"
        mv "$temp_file" "$JAR_PATH"
    }

    function runApplication() {
        if ! hash java 2>/dev/null; then
            echo "Java is not installed or not on your PATH. Please install it and try again." >&2
            exit -1
        fi

        BATECT_WRAPPER_SCRIPT_PATH="$SCRIPT_PATH" \
        HOSTNAME="$HOSTNAME" \
        java -Djava.net.useSystemProxies=true -jar "$JAR_PATH" "$@"
    }

    main "$@"
    exit $?
}
