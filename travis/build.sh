#!/usr/bin/env bash

set -euxo pipefail

./gradlew spotlessCheck
echo

./gradlew check
echo

./gradlew journeyTest
echo

./gradlew assembleRelease
echo
