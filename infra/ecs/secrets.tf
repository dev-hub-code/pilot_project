# Generated once and kept in Secrets Manager (and in the Terraform state). Never regenerate the encryption keys or
# the fingerprint key once there is data: what was encrypted with them could no longer be read.
resource "tls_private_key" "jwt" {
  algorithm = "RSA"
  rsa_bits  = 3072
}

resource "random_bytes" "data_key" {
  length = 32
}

resource "random_bytes" "fingerprint_key" {
  length = 32
}

locals {
  data_key_id = "k1"
}

resource "aws_secretsmanager_secret" "backend" {
  name                    = "${local.name}/backend"
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "backend" {
  secret_id = aws_secretsmanager_secret.backend.id
  secret_string = jsonencode({
    DB_PASSWORD                = random_password.db.result
    JWT_PRIVATE_KEY_LOCATION   = "base64:${base64encode(tls_private_key.jwt.private_key_pem_pkcs8)}"
    JWT_PUBLIC_KEY_LOCATION    = "base64:${base64encode(tls_private_key.jwt.public_key_pem)}"
    APP_CRYPTO_DATA_KEYS       = "${local.data_key_id}:${random_bytes.data_key.base64}"
    APP_CRYPTO_ACTIVE_KEY_ID   = local.data_key_id
    APP_CRYPTO_FINGERPRINT_KEY = random_bytes.fingerprint_key.base64
    MAIL_USERNAME              = aws_iam_access_key.ses_smtp.id
    MAIL_PASSWORD              = aws_iam_access_key.ses_smtp.ses_smtp_password_v4
  })
}
