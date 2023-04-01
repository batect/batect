#!/usr/bin/env bash

set -euo pipefail

VERSION=$1

if [ "$VERSION" = "latest" ]; then
    PACKAGE="docker-ce"
else
    PACKAGE="docker-ce=$VERSION"
fi

# This works around 'hash sum mismatch' issues.
# See https://blog.packagecloud.io/eng/2016/03/21/apt-hash-sum-mismatch/ for more details.
echo 'Acquire::CompressionTypes::Order:: "gz";' | sudo tee /etc/apt/apt.conf.d/99compression-workaround > /dev/null

sudo apt-get remove -y docker docker-engine docker.io containerd runc moby-runc containerd.io

sudo mkdir -m 0755 -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch="$(dpkg --print-architecture)" signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu "$(. /etc/os-release && echo "$VERSION_CODENAME")" stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get -y -o Dpkg::Options::="--force-confnew" --allow-downgrades install "$PACKAGE"
