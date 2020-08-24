#! /usr/bin/env bash

# Based on https://docs.docker.com/engine/security/https/ and https://gist.github.com/ivan-pinatti/6ad05557e526f1f32ca357d15139df83

set -euo pipefail

DAEMON_CERTS_DIR=/etc/docker/certs
CLIENT_CERTS_DIR=$HOME/.docker/certs
PASSPHRASE=abcd1234

sudo mkdir -p "$DAEMON_CERTS_DIR"
sudo rm -rf $DAEMON_CERTS_DIR/*
sudo chmod a+rx /etc/docker

mkdir -p "$CLIENT_CERTS_DIR"
sudo rm -rf $CLIENT_CERTS_DIR/*

echo "Generating CA key and cert..."
sudo openssl genrsa -aes256 -out "$DAEMON_CERTS_DIR/ca-key.pem" -passout pass:"$PASSPHRASE" 4096
sudo openssl req -new -x509 -days 365 -key "$DAEMON_CERTS_DIR/ca-key.pem" -sha256 -passin pass:"$PASSPHRASE" -subj "/CN=localhost/O=Internet/C=AU" -out "$DAEMON_CERTS_DIR/ca.pem"

echo "Generating daemon key and signing request..."
sudo openssl genrsa -out "$DAEMON_CERTS_DIR/server-key.pem" 4096
sudo openssl req -subj "/CN=localhost" -sha256 -new -key "$DAEMON_CERTS_DIR/server-key.pem" -out "$DAEMON_CERTS_DIR/server.csr"

echo "Generating daemon cert..."
cat <<EOF | sudo tee "$DAEMON_CERTS_DIR/extfile.cnf" > /dev/null
subjectAltName = DNS:localhost,IP:127.0.0.1
extendedKeyUsage = serverAuth
EOF
sudo openssl x509 -req -days 365 -sha256 -in "$DAEMON_CERTS_DIR/server.csr" -CA "$DAEMON_CERTS_DIR/ca.pem" -CAkey "$DAEMON_CERTS_DIR/ca-key.pem" -CAcreateserial -out "$DAEMON_CERTS_DIR/server-cert.pem" -extfile "$DAEMON_CERTS_DIR/extfile.cnf" -passin pass:"$PASSPHRASE"

echo "Generating client key and signing request..."
openssl genrsa -out "$CLIENT_CERTS_DIR/key.pem" 4096
openssl req -subj '/CN=client' -new -key "$CLIENT_CERTS_DIR/key.pem" -out "$CLIENT_CERTS_DIR/client.csr"
echo extendedKeyUsage = clientAuth > "$CLIENT_CERTS_DIR/extfile-client.cnf"
sudo openssl x509 -req -days 365 -sha256 -in "$CLIENT_CERTS_DIR/client.csr" -CA "$DAEMON_CERTS_DIR/ca.pem" -CAkey "$DAEMON_CERTS_DIR/ca-key.pem" -CAcreateserial -out "$CLIENT_CERTS_DIR/cert.pem" -extfile "$CLIENT_CERTS_DIR/extfile-client.cnf" -passin pass:"$PASSPHRASE"
sudo chown $USER "$CLIENT_CERTS_DIR/cert.pem"
ln -s "$DAEMON_CERTS_DIR/ca.pem" "$CLIENT_CERTS_DIR/ca.pem"

echo "Cleaning up and setting permissions..."
sudo rm -rf "$DAEMON_CERTS_DIR/server.csr" "$DAEMON_CERTS_DIR/extfile.cnf"
sudo rm -rf "$CLIENT_CERTS_DIR/client.csr" "$CLIENT_CERTS_DIR/extfile-client.cnf"
sudo chmod 0400 "$DAEMON_CERTS_DIR/ca-key.pem" "$CLIENT_CERTS_DIR/key.pem" "$DAEMON_CERTS_DIR/server-key.pem"
sudo chmod 0444 "$DAEMON_CERTS_DIR/ca.pem" "$DAEMON_CERTS_DIR/server-cert.pem" "$CLIENT_CERTS_DIR/cert.pem"

echo "Configuring daemon..."
sudo sed -i 's#ExecStart=.*#ExecStart=/usr/bin/dockerd --containerd=/run/containerd/containerd.sock#' /lib/systemd/system/docker.service

cat <<EOF | sudo tee "/etc/docker/daemon.json" > /dev/null
{
  "tlsverify": true,
  "tlscacert": "$DAEMON_CERTS_DIR/ca.pem",
  "tlscert": "$DAEMON_CERTS_DIR/server-cert.pem",
  "tlskey": "$DAEMON_CERTS_DIR/server-key.pem",
  "hosts": ["tcp://0.0.0.0:2376"]
}
EOF

echo "Restarting daemon..."
sudo systemctl daemon-reload
sudo service docker restart

echo "Setting environment variables for GitHub Actions..."
echo "::set-env name=DOCKER_CERT_PATH::$CLIENT_CERTS_DIR"
echo "::set-env name=DOCKER_TLS_VERIFY::1"
echo "::set-env name=DOCKER_HOST::tcp://localhost:2376"

echo "Done."
