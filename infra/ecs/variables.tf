variable "region" {
  description = "AWS region (Mumbai)."
  type        = string
  default     = "ap-south-1"
}

variable "domain" {
  description = "The site's domain; it is served at www.<domain>, and the bare domain is forwarded there by GoDaddy."
  type        = string
  default     = "sealease.in"
}

variable "image_tag" {
  description = "Tag of the backend and frontend images in ECR to run (infra/ecs/deploy.sh sets it to the git commit)."
  type        = string
  default     = "latest"
}

variable "backend_count" {
  description = "API tasks to run."
  type        = number
  default     = 1
}

variable "frontend_count" {
  description = "Web tasks to run."
  type        = number
  default     = 1
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.small"
}

variable "db_engine_version" {
  description = "PostgreSQL major version on RDS."
  type        = string
  default     = "18"
}

variable "db_multi_az" {
  description = "A standby database in a second zone (doubles the database cost)."
  type        = bool
  default     = false
}

variable "kafka_version" {
  type    = string
  default = "3.9.x"
}

variable "kafka_instance_type" {
  type    = string
  default = "kafka.t3.small"
}

variable "invoice_issuer_name" {
  description = "Seller name printed on invoices."
  type        = string
  default     = "Leasefleet Solutions Pvt. Ltd."
}

variable "invoice_issuer_address" {
  description = "Seller address printed on invoices."
  type        = string
  default     = "Akhade, Humgaon, Satara 415514"
}

variable "invoice_issuer_tax_id" {
  description = "Seller tax registration number printed on invoices (blank: none)."
  type        = string
  default     = ""
}
