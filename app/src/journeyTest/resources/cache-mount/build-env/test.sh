#! /usr/bin/env sh

if [ -f /cache/file ]; then
    echo 'File created in task exists'
else
    echo 'File created in task does not exist, creating it'
    touch /cache/file
fi

if [ -f /cache/file-from-image ]; then
    echo 'File created in image exists'
else
    echo 'File created in image does not exist'
fi
