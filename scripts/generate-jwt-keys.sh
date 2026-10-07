#!/usr/bin/env bash
# Generates an RSA-3072 key pair for signing access tokens (RS256) into <repo>/.secrets/.
# For local development only; production keys come from the deployment's secret manager.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
dir="$repo_root/.secrets"
mkdir -p "$dir"
chmod 700 "$dir"

if [[ -e "$dir/jwt-private.pem" && "${1:-}" != "--force" ]]; then
  echo "Keys already exist in $dir (use --force to replace; this signs out every user)." >&2
  exit 1
fi

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$dir/jwt-private.pem" 2>/dev/null
openssl pkey -in "$dir/jwt-private.pem" -pubout -out "$dir/jwt-public.pem"
chmod 600 "$dir/jwt-private.pem"

cat <<MSG
Generated $dir/jwt-private.pem and jwt-public.pem. Add to .env:

JWT_PRIVATE_KEY_LOCATION=file:$dir/jwt-private.pem
JWT_PUBLIC_KEY_LOCATION=file:$dir/jwt-public.pem
MSG
