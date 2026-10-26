#!/usr/bin/env bash
# Creates .secrets/production.env (git-ignored): the production API's settings and secrets, with fresh JWT and
# field-encryption keys, the Lightsail database from infra/lightsail, and the SMTP settings from .env.
# Run once. BACK THE FILE UP (e.g. in a password manager): without its encryption keys the KYC documents, tax ids and
# bank accounts in the database can never be read again. It refuses to overwrite an existing file.
set -euo pipefail
cd "$(dirname "$0")/.."
out=.secrets/production.env
if [[ -e "$out" ]]; then
  echo "$out already exists; not overwriting its keys." >&2
  exit 1
fi
mail() { grep -E "^$1=" .env | head -1 | cut -d= -f2-; }
for key in MAIL_HOST MAIL_PORT MAIL_USERNAME MAIL_PASSWORD; do
  [[ -n "$(mail $key)" ]] || { echo "Set $key in .env first (the production site sends email through it)." >&2; exit 1; }
done

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$work/jwt-private.pem" 2>/dev/null
openssl pkey -in "$work/jwt-private.pem" -pubout -out "$work/jwt-public.pem"
key_id="k$(date +%Y%m%d)"

mkdir -p .secrets
umask 077
cat > "$out" <<ENV
SPRING_PROFILES_ACTIVE=prod
DB_URL=$(terraform -chdir=infra/lightsail output -raw database_url)
DB_USERNAME=sealease
DB_PASSWORD=$(terraform -chdir=infra/lightsail output -raw database_password)
JWT_ISSUER=https://sealease.in
JWT_AUDIENCE=sealease-api
JWT_PRIVATE_KEY_LOCATION=base64:$(base64 -w0 "$work/jwt-private.pem")
JWT_PUBLIC_KEY_LOCATION=base64:$(base64 -w0 "$work/jwt-public.pem")
APP_CRYPTO_DATA_KEYS=${key_id}:$(openssl rand -base64 32)
APP_CRYPTO_ACTIVE_KEY_ID=${key_id}
APP_CRYPTO_FINGERPRINT_KEY=$(openssl rand -base64 32)
APP_KAFKA_CREATETOPICS=true
APP_PUBLIC_URL=https://www.sealease.in
APP_CORS_ALLOWED_ORIGINS=https://www.sealease.in
APP_NOTIFICATION_EMAIL_ENABLED=true
MAIL_HOST=$(mail MAIL_HOST)
MAIL_PORT=$(mail MAIL_PORT)
MAIL_USERNAME=$(mail MAIL_USERNAME)
MAIL_PASSWORD=$(mail MAIL_PASSWORD)
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
INVOICE_ISSUER_NAME=Leasefleet Solutions Pvt. Ltd.
INVOICE_ISSUER_ADDRESS=Akhade, Humgaon, Satara 415514
ENV
echo "Wrote $out. Back it up now: it holds the only copy of the encryption keys."
