#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/opt/livecircle"
SERVICE_USER="livecircle"
PORT="${PORT:-8787}"

if [ "$(id -u)" -ne 0 ]; then
  echo "Run as root." >&2
  exit 1
fi

apt-get update
apt-get install -y ca-certificates curl

if ! command -v node >/dev/null 2>&1; then
  curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
  apt-get install -y nodejs
fi

id -u "$SERVICE_USER" >/dev/null 2>&1 || useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin "$SERVICE_USER"
mkdir -p "$APP_DIR"
cp -r ./server/* "$APP_DIR"/
chown -R "$SERVICE_USER:$SERVICE_USER" "$APP_DIR"

cat >/etc/systemd/system/livecircle.service <<EOF
[Unit]
Description=LiveCircle relay server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$SERVICE_USER
WorkingDirectory=$APP_DIR
Environment=PORT=$PORT
Environment=LIVECIRCLE_TTL_MS=900000
ExecStart=/usr/bin/node $APP_DIR/server.js
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now livecircle

if command -v ufw >/dev/null 2>&1; then
  ufw allow "$PORT"/tcp || true
fi

echo "LiveCircle relay installed."
echo "Health check: http://SERVER_PUBLIC_IP:$PORT/api/health"
