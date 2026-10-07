#!/usr/bin/env bash
# Prints fresh field-encryption settings for .env (local development only; production keys come
# from the deployment's secret manager). To rotate: append a new keyId:key to APP_CRYPTO_DATA_KEYS,
# point APP_CRYPTO_ACTIVE_KEY_ID at it, and keep the old entries so existing data stays readable.
# Never change APP_CRYPTO_FINGERPRINT_KEY once data exists: duplicate detection depends on it.
set -euo pipefail
key_id="k$(date +%Y%m%d)"
cat <<MSG
APP_CRYPTO_DATA_KEYS=${key_id}:$(openssl rand -base64 32)
APP_CRYPTO_ACTIVE_KEY_ID=${key_id}
APP_CRYPTO_FINGERPRINT_KEY=$(openssl rand -base64 32)
MSG
