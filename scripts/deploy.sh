#!/usr/bin/env bash
# Deploys the current commit to the Lightsail server (infra/lightsail): builds the backend and frontend images here,
# copies them to the server with the compose files and .secrets/production.env, and restarts what changed.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Commit or stash your changes first: the images are tagged with the commit they are built from." >&2
  exit 1
fi
[[ -f .secrets/production.env ]] || { echo "Run scripts/production-env.sh first." >&2; exit 1; }
tag=$(git rev-parse --short=12 HEAD)
host=$(terraform -chdir=infra/lightsail output -raw static_ip)
ssh_opts=(-i ~/.ssh/sealease_lightsail -o StrictHostKeyChecking=accept-new)
remote() { ssh "${ssh_opts[@]}" "ubuntu@$host" "$@"; }

for app in backend frontend; do
  docker build --platform linux/amd64 -t "sealease-$app:$tag" "$app"
done

echo "Copying images to $host…"
docker save "sealease-backend:$tag" "sealease-frontend:$tag" | gzip -1 | remote 'gunzip | docker load'
scp "${ssh_opts[@]}" deploy/compose.yaml deploy/Caddyfile "ubuntu@$host:/opt/sealease/"
scp "${ssh_opts[@]}" .secrets/production.env "ubuntu@$host:/opt/sealease/backend.env"

remote "cd /opt/sealease && chmod 600 backend.env && echo TAG=$tag > .env \
  && docker compose up -d --remove-orphans --wait --wait-timeout 300 && docker image prune -af --filter until=168h >/dev/null"
remote "cd /opt/sealease && docker compose ps --format 'table {{.Service}}\t{{.Status}}'"
echo "Deployed $tag to https://www.sealease.in"
