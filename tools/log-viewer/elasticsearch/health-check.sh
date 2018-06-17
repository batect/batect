#!/usr/bin/env sh

set -e

HOST=${HOST:-localhost}
PORT=${PORT:-9200}

RESPONSE=$(curl "http://$HOST:$PORT/_cat/health" --fail --show-error --silent || exit 1)

if [[ "$RESPONSE" == *"docker-cluster green"* ]]; then
    exit 0
else
    echo "Unexpected response from service: $RESPONSE"
    exit 1
fi
