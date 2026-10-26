# Amazon SES sends the app's email from no-reply@<domain>. The domain is verified by the DKIM records in the
# godaddy_dns_records output. New SES accounts start in the sandbox (only verified addresses receive mail) until AWS
# grants production access.
resource "aws_sesv2_email_identity" "domain" {
  email_identity = var.domain
}

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
