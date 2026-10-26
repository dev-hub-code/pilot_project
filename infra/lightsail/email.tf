# Amazon SES sends the site's email as no-reply@<domain>. The domain is verified by the DKIM records in the
# email_dns_records output (add them in GoDaddy). New SES accounts start in the sandbox: only addresses verified in SES
# receive mail, at most 200 a day, until AWS grants production access.
variable "domain" {
  type    = string
  default = "sealease.in"
}

resource "aws_sesv2_email_identity" "domain" {
  email_identity = var.domain
}

# SMTP credentials for the API (MAIL_USERNAME / MAIL_PASSWORD): an IAM user that may only send as @<domain>.
resource "aws_iam_user" "ses_smtp" {
  name = "${local.name}-ses-smtp"
}

resource "aws_iam_user_policy" "ses_smtp" {
  name = "send-email"
  user = aws_iam_user.ses_smtp.name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["ses:SendRawEmail", "ses:SendEmail"]
      Resource = "*"
      Condition = {
        StringLike = { "ses:FromAddress" = "*@${var.domain}" }
      }
    }]
  })
}

resource "aws_iam_access_key" "ses_smtp" {
  user = aws_iam_user.ses_smtp.name
}

output "email_dns_records" {
  description = "Add in GoDaddy (DNS > Manage DNS). Names are relative to the domain."
  # The domain's DMARC record is GoDaddy's (p=quarantine); SES's DKIM signature satisfies it.
  value = [for token in aws_sesv2_email_identity.domain.dkim_signing_attributes[0].tokens : {
    type  = "CNAME"
    name  = "${token}._domainkey"
    value = "${token}.dkim.amazonses.com"
  }]
}

output "smtp" {
  description = "The API's SMTP settings (scripts/production-env.sh)."
  value = {
    host     = "email-smtp.${var.region}.amazonaws.com"
    port     = 587
    username = aws_iam_access_key.ses_smtp.id
  }
}

output "smtp_password" {
  value     = aws_iam_access_key.ses_smtp.ses_smtp_password_v4
  sensitive = true
}
