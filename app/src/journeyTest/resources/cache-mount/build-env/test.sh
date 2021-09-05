#! /usr/bin/env sh

if [ -f /cache/file ]; then
    echo 'File created in task exists'
else
    echo 'File created in task does not exist, creating it'
    touch /cache/file
fi
