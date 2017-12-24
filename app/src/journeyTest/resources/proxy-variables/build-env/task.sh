#!/usr/bin/env sh

echo "At build time, environment variables were:"
cat /build-vars.txt
echo
echo "At runtime, environment variables are:"
echo "http_proxy: $http_proxy"
echo "https_proxy: $https_proxy"
echo "ftp_proxy: $ftp_proxy"
echo "no_proxy: $no_proxy"
