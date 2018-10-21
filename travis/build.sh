#!/usr/bin/env bash

set -euo pipefail

echo "Linting..."
./gradlew spotlessCheck
echo

echo "Building..."
./gradlew build --info --stacktrace
echo

echo "Running unit tests..."
./gradlew check
echo

echo "Generating code coverage report..."
./gradlew jacocoTestReport
echo

echo "Running journey tests..."
./gradlew journeyTest
echo

echo "Assembling release..."
./gradlew assembleRelease
echo
