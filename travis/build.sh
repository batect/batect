#!/usr/bin/env bash

set -euo pipefail

echo "Linting..."
./gradlew spotlessCheck
echo

echo "Building..."
./gradlew build
echo

echo "Running unit tests..."
./gradlew check
echo

echo "Running journey tests..."
./gradlew journeyTest
echo

echo "Assembling release..."
./gradlew assembleRelease
echo

echo "Publishing documentation..."
./gradlew docs:publish
echo
