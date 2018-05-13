#!/usr/bin/env sh

set -euo pipefail

echo "User: $(id -un)"
echo "Group: $(id -gn)"
echo "Home directory: $HOME"

if [ -d $HOME ]; then
    echo "Home directory exists"
    echo "Home directory owned by user: $(stat -c '%U' $HOME)"
    echo "Home directory owned by group: $(stat -c '%G' $HOME)"
else
    echo "Home directory does not exist"
fi

touch /output/created-file
ls -la /output
