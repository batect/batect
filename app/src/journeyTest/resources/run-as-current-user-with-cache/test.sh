#!/usr/bin/env sh

set -euo pipefail

if [ -d /cache ]; then
    echo "/cache exists"

    touch /cache/created-file

    if [ -f /cache/created-file ]; then
        echo "/cache/created-file created"
    else
        echo "/cache/created-file could not be created"
    fi
else
    echo "/cache does not exist"
fi

if [ -d /home/special-place/cache ]; then
    echo "/home/special-place/cache exists"

    touch /home/special-place/cache/created-file

    if [ -f /home/special-place/cache/created-file ]; then
        echo "/home/special-place/cache/created-file created"
    else
        echo "/home/special-place/cache/created-file could not be created"
    fi
else
    echo "/home/special-place/cache does not exist"
fi


if [ -d /home/special-place/subdir/cache ]; then
    echo "/home/special-place/subdir/cache exists"

    touch /home/special-place/subdir/cache/created-file

    if [ -f /home/special-place/subdir/cache/created-file ]; then
        echo "/home/special-place/subdir/cache/created-file created"
    else
        echo "/home/special-place/subdir/cache/created-file could not be created"
    fi
else
    echo "/home/special-place/subdir/cache does not exist"
fi
