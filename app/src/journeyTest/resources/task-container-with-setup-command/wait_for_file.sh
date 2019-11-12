#! /usr/bin/env bash

set -euo pipefail

for ((i = 1; i <= 5; i++)); do
  if [ -f /message.txt ]; then
    cat /message.txt
    exit 123
  else
    sleep 1
  fi
done

echo "/message.txt did not exist after 5 seconds, failing."
exit 1
