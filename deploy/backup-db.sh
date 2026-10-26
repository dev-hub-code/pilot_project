#!/usr/bin/env bash
# Nightly database backup, run on the server by cron (deploy/backup-db.cron): dumps the database (pg_dump custom
# format) and uploads it to the backups bucket (infra/lightsail/storage.tf). The bucket deletes dumps after 30 days.
# The database settings come from backend.env, the upload credentials from backup.env; that key may only add files.
# Restore with: pg_restore --clean --if-exists --no-owner -d "<connection>" <file>.dump
set -euo pipefail
cd /opt/sealease

setting() { grep -E "^$1=" "$2" | head -1 | cut -d= -f2-; }
db_url=$(setting DB_URL backend.env)
db_host=$(sed -E 's#jdbc:postgresql://([^:/]+).*#\1#' <<<"$db_url")
db_name=$(sed -E 's#jdbc:postgresql://[^/]+/([^?]+).*#\1#' <<<"$db_url")
bucket=$(setting BACKUP_BUCKET backup.env)
file="sealease-$(date -u +%Y%m%d-%H%M%S).dump"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

docker run --rm -e PGPASSWORD="$(setting DB_PASSWORD backend.env)" -v "$work:/out" postgres:18 \
  pg_dump "host=$db_host dbname=$db_name user=$(setting DB_USERNAME backend.env) sslmode=require" -Fc -f "/out/$file"
docker run --rm --env-file backup.env -v "$work:/out:ro" amazon/aws-cli:2.31.0 \
  s3 cp "/out/$file" "s3://$bucket/daily/$file" --only-show-errors
echo "$(date -u +%FT%TZ) uploaded $file ($(du -h "$work/$file" | cut -f1)) to s3://$bucket/daily/"
