#!/usr/bin/env bash

set -euo pipefail

PACKAGE=${1:-docker-ce}

# This works around 'hash sum mismatch' issues.
# See https://blog.packagecloud.io/eng/2016/03/21/apt-hash-sum-mismatch/ for more details.
echo 'Acquire::CompressionTypes::Order:: "gz";' | sudo tee /etc/apt/apt.conf.d/99compression-workaround

curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
sudo apt-get update
sudo apt-get -y -o Dpkg::Options::="--force-confnew" --allow-downgrades install "$PACKAGE"
