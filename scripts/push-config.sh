#!/usr/bin/env bash
# Copies .secrets/production.env to the server and restarts the API with it (no image rebuild).
set -euo pipefail
cd "$(dirname "$0")/.."
host=$(terraform -chdir=infra/lightsail output -raw static_ip)
ssh_opts=(-i ~/.ssh/sealease_lightsail -o StrictHostKeyChecking=accept-new)
scp "${ssh_opts[@]}" .secrets/production.env "ubuntu@$host:/opt/sealease/backend.env"
ssh "${ssh_opts[@]}" "ubuntu@$host" "cd /opt/sealease && chmod 600 backend.env \
  && docker compose up -d --wait --wait-timeout 300 backend && docker compose ps --format 'table {{.Service}}\t{{.Status}}'"
