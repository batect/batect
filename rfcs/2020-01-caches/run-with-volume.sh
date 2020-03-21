#! /usr/bin/env bash

set -euo pipefail

CACHE_VOLUME_NAME=abacus-go-cache

echo "Cleaning up from previous runs..."
docker volume rm $CACHE_VOLUME_NAME || true
echo

echo "Running container..."
docker run --rm -it -v $(pwd):/code -w /code -e GOCACHE=/go/cache -v $CACHE_VOLUME_NAME:/go golang:1.13.7-stretch bash
