#!/usr/bin/env bash
# Writes the development server's secrets (git-ignored), after `terraform -chdir=infra/lightsail apply`:
#   .secrets/lightsail.env         the API's settings: fresh JWT and field-encryption keys (made once and then kept), and
#                                  the database, SES and S3 settings from Terraform (refreshed each run)
#   .secrets/lightsail-backup.env  the add-only key for the nightly database backups
# Lines you add yourself are kept, e.g. a fallback SMTP server (Brevo) for mail SES cannot send:
#   MAIL_FALLBACK_HOST=smtp-relay.brevo.com  MAIL_FALLBACK_USERNAME=<SMTP login>  MAIL_FALLBACK_PASSWORD=<SMTP key>
# BACK UP .secrets/lightsail.env (e.g. in a password manager): without its encryption keys the KYC documents, tax ids
# and bank accounts in the database can never be read again.
set -euo pipefail
cd "$(dirname "$0")/../.."
out=.secrets/lightsail.env
tf() { terraform -chdir=infra/lightsail output "$@"; }
json() { tf -json "$1" | jq -r ".$2"; }
mkdir -p .secrets
umask 077

if [[ ! -f "$out" ]]; then
  work=$(mktemp -d)
  trap 'rm -rf "$work"' EXIT
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$work/jwt-private.pem" 2>/dev/null
  openssl pkey -in "$work/jwt-private.pem" -pubout -out "$work/jwt-public.pem"
  key_id="k$(date +%Y%m%d)"
  cat > "$out" <<ENV
# Generated once by infra/lightsail/env.sh; keep these.
JWT_PRIVATE_KEY_LOCATION=base64:$(base64 -w0 "$work/jwt-private.pem")
JWT_PUBLIC_KEY_LOCATION=base64:$(base64 -w0 "$work/jwt-public.pem")
APP_CRYPTO_DATA_KEYS=${key_id}:$(openssl rand -base64 32)
APP_CRYPTO_ACTIVE_KEY_ID=${key_id}
APP_CRYPTO_FINGERPRINT_KEY=$(openssl rand -base64 32)
ENV
  echo "Created $out with new keys. Back it up now: it holds the only copy of the encryption keys."
fi

# Everything below is rewritten on each run; the generated keys above are kept.
managed='DB_URL|DB_USERNAME|DB_PASSWORD|SPRING_PROFILES_ACTIVE|JWT_ISSUER|JWT_AUDIENCE|APP_KAFKA_CREATETOPICS|APP_PUBLIC_URL|APP_CORS_ALLOWED_ORIGINS|APP_NOTIFICATION_EMAIL_ENABLED|APP_NOTIFICATION_EMAIL_FROM|MAIL_HOST|MAIL_PORT|MAIL_USERNAME|MAIL_PASSWORD|MAIL_SMTP_AUTH|MAIL_SMTP_STARTTLS|INVOICE_ISSUER_NAME|INVOICE_ISSUER_ADDRESS|APP_DOCUMENTS_S3_BUCKET|APP_DOCUMENTS_S3_REGION|AWS_ACCESS_KEY_ID|AWS_SECRET_ACCESS_KEY|# Set by'
grep -vE "^($managed)" "$out" > "$out.new" || true
cat >> "$out.new" <<ENV
# Set by infra/lightsail/env.sh from Terraform on each run.
SPRING_PROFILES_ACTIVE=prod
DB_URL=$(tf -raw database_url)
DB_USERNAME=sealease
DB_PASSWORD=$(tf -raw database_password)
JWT_ISSUER=https://sealease.in
JWT_AUDIENCE=sealease-api
APP_KAFKA_CREATETOPICS=true
APP_PUBLIC_URL=https://www.sealease.in
APP_CORS_ALLOWED_ORIGINS=https://www.sealease.in
APP_NOTIFICATION_EMAIL_ENABLED=true
APP_NOTIFICATION_EMAIL_FROM=SeaLease <no-reply@sealease.in>
MAIL_HOST=$(json smtp host)
MAIL_PORT=$(json smtp port)
MAIL_USERNAME=$(json smtp username)
MAIL_PASSWORD=$(tf -raw smtp_password)
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
INVOICE_ISSUER_NAME=Leasefleet Solutions Pvt. Ltd.
INVOICE_ISSUER_ADDRESS=Akhade, Humgaon, Satara 415514
APP_DOCUMENTS_S3_BUCKET=$(json storage documents_bucket)
APP_DOCUMENTS_S3_REGION=$(json storage region)
AWS_ACCESS_KEY_ID=$(json storage documents_key_id)
AWS_SECRET_ACCESS_KEY=$(tf -raw documents_secret_key)
ENV
mv "$out.new" "$out"

cat > .secrets/lightsail-backup.env <<ENV
BACKUP_BUCKET=$(json storage backups_bucket)
AWS_DEFAULT_REGION=$(json storage region)
AWS_ACCESS_KEY_ID=$(json storage backups_key_id)
AWS_SECRET_ACCESS_KEY=$(tf -raw backups_secret_key)
ENV
echo "Wrote $out and .secrets/lightsail-backup.env. Next: infra/lightsail/deploy.sh"
