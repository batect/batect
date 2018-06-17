#!/usr/bin/env sh

set -e

HOST=${HOST:-localhost}
PORT=${PORT:-5601}

RESPONSE=$(curl "http://$HOST:$PORT/api/status" --fail --show-error --silent || exit 1)

if [[ $(echo "$RESPONSE" | jq -r '.status.overall.state') == "green" ]]; then
    exit 0
else
    echo "Unexpected response from service: $RESPONSE"
    exit 1
fi
