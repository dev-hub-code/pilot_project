#!/usr/bin/env bash
# Writes the S3 settings from infra/lightsail (storage.tf) into the git-ignored secrets: the documents bucket and its
# key into .secrets/production.env (replacing any earlier ones), and the backups bucket and its add-only key into
# .secrets/backup.env. Then run scripts/deploy.sh.
set -euo pipefail
cd "$(dirname "$0")/.."
env=.secrets/production.env
[[ -f "$env" ]] || { echo "Run scripts/production-env.sh first." >&2; exit 1; }
tf() { terraform -chdir=infra/lightsail output "$@"; }
storage() { tf -json storage | jq -r ".$1"; }

umask 077
grep -vE '^(APP_DOCUMENTS_S3_BUCKET|APP_DOCUMENTS_S3_REGION|AWS_ACCESS_KEY_ID|AWS_SECRET_ACCESS_KEY)=' "$env" > "$env.new" || true
cat >> "$env.new" <<ENV
APP_DOCUMENTS_S3_BUCKET=$(storage documents_bucket)
APP_DOCUMENTS_S3_REGION=$(storage region)
AWS_ACCESS_KEY_ID=$(storage documents_key_id)
AWS_SECRET_ACCESS_KEY=$(tf -raw documents_secret_key)
ENV
mv "$env.new" "$env"

cat > .secrets/backup.env <<ENV
BACKUP_BUCKET=$(storage backups_bucket)
AWS_DEFAULT_REGION=$(storage region)
AWS_ACCESS_KEY_ID=$(storage backups_key_id)
AWS_SECRET_ACCESS_KEY=$(tf -raw backups_secret_key)
ENV
echo "Wrote the documents settings to $env and .secrets/backup.env."
