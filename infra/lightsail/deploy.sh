#!/usr/bin/env bash
# Deploys the main branch, as it is on GitHub (origin/main), to the development server (infra/lightsail): builds the
# backend and frontend images here, copies them to the server over SSH with the compose files and secrets, and
# restarts what changed. No registry.
#   infra/lightsail/deploy.sh
# Whatever branch is checked out, and any local changes, are left alone: the build uses a clean copy of origin/main.
# First time: terraform -chdir=infra/lightsail apply, then infra/lightsail/env.sh, then point GoDaddy at static_ip.
set -euo pipefail
cd "$(dirname "$0")/../.."
here=infra/lightsail
[[ -f .secrets/lightsail.env ]] || { echo "Run $here/env.sh first." >&2; exit 1; }

# Always main as pushed to GitHub, built from a clean temporary checkout of it.
git fetch --quiet origin main
tag=$(git rev-parse --short=12 origin/main)
src=$(mktemp -d)
trap 'git worktree remove --force "$src" >/dev/null 2>&1 || rm -rf "$src"' EXIT
git worktree add --quiet --detach "$src" origin/main
host=$(terraform -chdir=$here output -raw static_ip)
ssh_opts=(-i ~/.ssh/sealease_lightsail -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15)
remote() { ssh "${ssh_opts[@]}" "ubuntu@$host" "$@"; }

echo "Deploying main at $tag ($(git log -1 --format=%s origin/main)) to $host"
# A new server installs Docker on first boot; wait for that.
remote 'cloud-init status --wait >/dev/null; docker version --format "Docker {{.Server.Version}}"'

for app in backend frontend; do
  docker build --platform linux/amd64 -t "sealease-$app:$tag" "$src/$app"
done
echo "Copying images to $host…"
docker save "sealease-backend:$tag" "sealease-frontend:$tag" | gzip -1 | remote 'gunzip | docker load'

scp "${ssh_opts[@]}" "$src/$here/compose.yaml" "$src/$here/Caddyfile" "$src/$here/backup-db.sh" "$src/$here/backup-db.cron" \
  .secrets/lightsail-backup.env "ubuntu@$host:/opt/sealease/"
grep -v '^#' .secrets/lightsail.env | remote 'umask 077; cat > /opt/sealease/backend.env'
echo "TAG=$tag" | remote 'cat > /opt/sealease/.env'

remote "cd /opt/sealease && mv lightsail-backup.env backup.env && chmod 600 backup.env && chmod 700 backup-db.sh \
  && sudo install -m 644 -o root -g root backup-db.cron /etc/cron.d/sealease-backup && rm backup-db.cron \
  && docker compose up -d --remove-orphans --wait --wait-timeout 600 \
  && docker image prune -af --filter until=168h >/dev/null \
  && docker compose ps --format 'table {{.Service}}\t{{.Status}}'"
echo "Deployed $tag: https://www.sealease.in (server $host)"
