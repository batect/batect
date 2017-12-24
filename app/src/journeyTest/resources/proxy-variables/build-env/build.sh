#!/usr/bin/env sh

echo "http_proxy: $http_proxy" > /build-vars.txt
echo "https_proxy: $https_proxy" >> /build-vars.txt
echo "ftp_proxy: $ftp_proxy" >> /build-vars.txt
echo "no_proxy: $no_proxy" >> /build-vars.txt
