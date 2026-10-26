# Two private S3 buckets: investors' documents (KYC, bank proofs, ticket attachments, plan photos), and the database
# backups the server uploads every night (deploy/backup-db.sh). Bucket names carry the account id, as S3 names are
# global. Each has its own IAM user, with only what it needs: the API reads and writes documents; the backup job can
# add dumps but never read or delete them, so a leaked backup key cannot erase or leak the backups.
data "aws_caller_identity" "current" {}

locals {
  documents_bucket = "${local.name}-documents-${data.aws_caller_identity.current.account_id}"
  backups_bucket   = "${local.name}-db-backups-${data.aws_caller_identity.current.account_id}"
}

variable "backup_retention_days" {
  description = "Database dumps older than this are deleted."
  type        = number
  default     = 30
}

# ------------------------------------------------------------------------------------------------ documents

resource "aws_s3_bucket" "documents" {
  bucket = local.documents_bucket
}

# Overwritten or deleted files can be recovered for 90 days.
resource "aws_s3_bucket_versioning" "documents" {
  bucket = aws_s3_bucket.documents.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "documents" {
  bucket     = aws_s3_bucket.documents.id
  depends_on = [aws_s3_bucket_versioning.documents]
  rule {
    id     = "expire-old-versions"
    status = "Enabled"
    filter {}
    noncurrent_version_expiration {
      noncurrent_days = 90
    }
    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

# ------------------------------------------------------------------------------------------------ backups

resource "aws_s3_bucket" "backups" {
  bucket = local.backups_bucket
}

resource "aws_s3_bucket_lifecycle_configuration" "backups" {
  bucket = aws_s3_bucket.backups.id
  rule {
    id     = "expire-old-dumps"
    status = "Enabled"
    filter {}
    expiration {
      days = var.backup_retention_days
    }
    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

# ------------------------------------------------------------------------------------------------ both buckets

resource "aws_s3_bucket_public_access_block" "private" {
  for_each                = { documents = aws_s3_bucket.documents.id, backups = aws_s3_bucket.backups.id }
  bucket                  = each.value
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "private" {
  for_each = { documents = aws_s3_bucket.documents.id, backups = aws_s3_bucket.backups.id }
  bucket   = each.value
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "private" {
  for_each = { documents = aws_s3_bucket.documents.id, backups = aws_s3_bucket.backups.id }
  bucket   = each.value
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Refuse any request not made over HTTPS.
resource "aws_s3_bucket_policy" "tls_only" {
  for_each = { documents = aws_s3_bucket.documents, backups = aws_s3_bucket.backups }
  bucket   = each.value.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [each.value.arn, "${each.value.arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })
  depends_on = [aws_s3_bucket_public_access_block.private]
}

# ------------------------------------------------------------------------------------------------ access

resource "aws_iam_user" "documents" {
  name = "${local.name}-documents"
}

resource "aws_iam_user_policy" "documents" {
  name = "read-write-documents"
  user = aws_iam_user.documents.name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:GetObject", "s3:PutObject"]
      Resource = "${aws_s3_bucket.documents.arn}/*"
    }]
  })
}

resource "aws_iam_access_key" "documents" {
  user = aws_iam_user.documents.name
}

resource "aws_iam_user" "backups" {
  name = "${local.name}-db-backup"
}

resource "aws_iam_user_policy" "backups" {
  name = "add-backups"
  user = aws_iam_user.backups.name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:PutObject"]
      Resource = "${aws_s3_bucket.backups.arn}/*"
    }]
  })
}

resource "aws_iam_access_key" "backups" {
  user = aws_iam_user.backups.name
}

output "storage" {
  description = "Bucket names and the access key ids (secrets: documents_secret_key, backups_secret_key)."
  value = {
    region           = var.region
    documents_bucket = aws_s3_bucket.documents.bucket
    documents_key_id = aws_iam_access_key.documents.id
    backups_bucket   = aws_s3_bucket.backups.bucket
    backups_key_id   = aws_iam_access_key.backups.id
  }
}

output "documents_secret_key" {
  value     = aws_iam_access_key.documents.secret
  sensitive = true
}

output "backups_secret_key" {
  value     = aws_iam_access_key.backups.secret
  sensitive = true
}
