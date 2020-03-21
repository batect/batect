#! /usr/bin/env bash

set -euo pipefail

CACHE_DIR="$(pwd)/.caches/go-cache"

echo "Cleaning up from previous runs..."
sudo rm -rf "$CACHE_DIR"
echo

echo "Running container..."
mkdir -p "$CACHE_DIR"
docker run --rm -it -v $(pwd):/code -w /code -e GOCACHE=/go/cache -v "$CACHE_DIR:/go:delegated" golang:1.13.7-stretch bash
