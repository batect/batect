#! /usr/bin/env bash

set -euo pipefail

COMMIT_DATE=$(TZ=UTC git show --quiet --date='format-local:%Y-%m-%dT%H:%M:%SZ' --format="%cd" HEAD)
VERSION=$(git describe --dirty --candidates=0)
ORG="batect"
REPO="batect"
PACKAGE="batect"
VERSION_SLUG="$ORG/$REPO/$PACKAGE/$VERSION"

echo "Creating version..."

jfrog bt version-create \
    --user="$BINTRAY_USER" --key="$BINTRAY_KEY" \
    --released="$COMMIT_DATE" \
    --vcs-tag="$VERSION" \
    "$VERSION_SLUG"

echo
echo "Uploading files..."

jfrog bt upload \
    --publish \
    --user="$BINTRAY_USER" --key="$BINTRAY_KEY" \
    "build/release/*" "$VERSION_SLUG" "$VERSION/bin/"

echo
echo "Publishing version..."

jfrog bt version-publish \
    --user="$BINTRAY_USER" --key="$BINTRAY_KEY" \
    "$VERSION_SLUG"

echo
echo "Done."
