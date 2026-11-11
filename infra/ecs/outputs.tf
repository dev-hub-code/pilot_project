output "godaddy_dns_records" {
  description = "Add these in GoDaddy (DNS > Manage DNS for the domain). Names are relative to the domain."
  value = concat(
    [for o in aws_acm_certificate.site.domain_validation_options : {
      purpose = "HTTPS certificate (${o.domain_name})"
      type    = o.resource_record_type
      name    = trimsuffix(trimsuffix(o.resource_record_name, "."), ".${var.domain}")
      value   = trimsuffix(o.resource_record_value, ".")
    }],
    # Email signing: add the CNAME records SendGrid shows under Settings > Sender Authentication > Domain.
    [
      {
        purpose = "Email policy (DMARC)"
        type    = "TXT"
        name    = "_dmarc"
        value   = "v=DMARC1; p=quarantine; rua=mailto:dmarc@${var.domain}"
      },
      {
        purpose = "The site"
        type    = "CNAME"
        name    = "www"
        value   = aws_lb.main.dns_name
      },
    ],
  )
}

output "godaddy_forwarding" {
  description = "In GoDaddy, forward the bare domain (Domain > Forwarding) here, permanent (301)."
  value       = local.site_url
}

output "ecr_repositories" {
  value = { for k, r in aws_ecr_repository.app : k => r.repository_url }
}

output "load_balancer_dns" {
  value = aws_lb.main.dns_name
}

output "database_endpoint" {
  value = aws_db_instance.main.address
}

output "region" {
  value = var.region
}

output "documents_bucket" {
  value = aws_s3_bucket.documents.bucket
}

output "availability_zones" {
  description = "The zones every tier is spread over."
  value       = local.azs
}
