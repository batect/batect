#!/usr/bin/env bash

echo "This is some normal output"
echo "This is some error output" 1>&2
exit 1
