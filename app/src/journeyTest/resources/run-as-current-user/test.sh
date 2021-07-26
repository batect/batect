#!/usr/bin/env sh

set -euo pipefail

# Running 'ls' here works around an issue with Docker for Mac's gRPC file sharing implementation where the two
# 'stat' invocations below return 'root' instead of the correct user.
# Confirmed bug is present in Docker for Mac 3.1.0. Switching to legacy implementation resolves the issue.
echo "Home directory contents:"
ls -la $HOME

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

if [ -f /etc/hosts ]; then
    echo "/etc/hosts exists"
else
    echo "/etc/hosts does not exist"
fi

touch /output/created-file

echo "/output contents:"
ls -la /output
