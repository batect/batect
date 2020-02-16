#!/usr/bin/env bash

{
    set -euo pipefail

    # This file is part of batect.
    # Do not modify this file, it will be overwritten next time you upgrade batect.
    # You should commit this file to version control alongside the rest of your project. It should not be installed globally.
    # For more information, visit https://github.com/batect/batect.

    VERSION="VERSION-GOES-HERE"
    DOWNLOAD_URL_ROOT=${BATECT_DOWNLOAD_URL_ROOT:-"https://dl.bintray.com/batect/batect"}
    DOWNLOAD_URL=${BATECT_DOWNLOAD_URL:-"$DOWNLOAD_URL_ROOT/$VERSION/bin/batect-$VERSION.jar"}
    QUIET_DOWNLOAD=${BATECT_QUIET_DOWNLOAD:-false}

    ROOT_CACHE_DIR=${BATECT_CACHE_DIR:-"$HOME/.batect/cache"}
    CACHE_DIR="$ROOT_CACHE_DIR/$VERSION"
    JAR_PATH="$CACHE_DIR/batect-$VERSION.jar"

    SCRIPT_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

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
        checkForCurl

        echo "Downloading batect version $VERSION from $DOWNLOAD_URL..."
        mkdir -p "$CACHE_DIR"
        temp_file=$(mktemp)

        if [[ $QUIET_DOWNLOAD == 'true' ]]; then
            curl --fail --show-error --location --output "$temp_file" "$DOWNLOAD_URL"
        else
            curl -# --fail --show-error --location --output "$temp_file" "$DOWNLOAD_URL"
        fi

        mv "$temp_file" "$JAR_PATH"
    }

    function runApplication() {
        checkForJava

        java_version=$(getJavaVersion)
        java_version_major=$(extractJavaMajorVersion "$java_version")

        if (( java_version_major >= 9 )); then
            JAVA_OPTS="--add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED"
        else
            JAVA_OPTS=""
        fi

        BATECT_WRAPPER_SCRIPT_DIR="$SCRIPT_PATH" \
        HOSTNAME="$HOSTNAME" \
            java \
            -Djava.net.useSystemProxies=true \
            $JAVA_OPTS \
            -jar "$JAR_PATH" \
            "$@"
    }

    function checkForCurl() {
        if ! hash curl 2>/dev/null; then
            echo "curl is not installed or not on your PATH. Please install it and try again." >&2
            exit 1
        fi
    }

    function checkForJava() {
        if ! hash java 2>/dev/null; then
            echo "Java is not installed or not on your PATH. Please install it and try again." >&2
            exit 1
        fi

        java_version=$(getJavaVersion)
        java_version_major=$(extractJavaMajorVersion "$java_version")
        java_version_minor=$(extractJavaMinorVersion "$java_version")

        if (( java_version_major < 1 || ( java_version_major == 1 && java_version_minor <= 7 ) )); then
            echo "The version of Java that is available on your PATH is version $java_version, but version 1.8 or greater is required."
            echo "If you have a newer version of Java installed, please make sure your PATH is set correctly."
            exit 1
        fi
    }

    function getJavaVersion() {
        java -version 2>&1 | head -n1 | sed -En ';s/.* version "([0-9]+)(\.([0-9]+))?.*".*/\1.\3/p;'
    }

    function extractJavaMajorVersion() {
        java_version=$1

        echo "${java_version%.*}"
    }

    function extractJavaMinorVersion() {
        java_version=$1
        java_version_minor="${java_version#*.}"

        echo "${java_version_minor:-0}"
    }

    main "$@"
    exit $?
}
