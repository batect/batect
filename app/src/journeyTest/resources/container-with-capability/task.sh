#!/usr/bin/env sh

set -euo pipefail

# Adapted from https://stackoverflow.com/a/32144661/1668119

if ip link add dummy0 type dummy ; then
    ip link delete dummy0

    echo "Container has capability"
    exit 0
else
    echo "Container does not have capability"
    exit 1
fi
