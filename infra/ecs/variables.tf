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
  description = "Tag of the backend and frontend images in ECR to run. Blank (the default) keeps the one live now, recorded in SSM by every deployment (CI/CD and infra/ecs/deploy.sh), so a plain terraform apply never rolls the app back."
  type        = string
  default     = ""
}

variable "github_repository" {
  description = "GitHub repository (owner/name) whose main-branch deployments may push images and roll out the services."
  type        = string
  default     = "dev-hub-code/pilot_project"
}

variable "az_count" {
  description = "Availability zones to spread every tier over (Kafka runs one broker in each). 2 survives a zone outage; 3 also keeps Kafka's three-way replication."
  type        = number
  default     = 2
  validation {
    condition     = var.az_count >= 2
    error_message = "High availability needs at least two zones."
  }
}

variable "nat_gateway" {
  description = "Outbound internet for the app tasks: none (public IPs, cheapest), single, or per_az (see network.tf)."
  type        = string
  default     = "none"
  validation {
    condition     = contains(["none", "single", "per_az"], var.nat_gateway)
    error_message = "nat_gateway is none, single or per_az."
  }
}

variable "fargate_spot" {
  description = "Run each service's tasks beyond the first on Fargate Spot (about 70% cheaper; AWS may reclaim them with two minutes' notice, and ECS replaces them). The first task is always on-demand."
  type        = bool
  default     = true
}

variable "backend_cpu" {
  description = "API task CPU units (1024 = 1 vCPU)."
  type        = number
  default     = 1024
}

variable "backend_memory" {
  description = "API task memory (MiB)."
  type        = number
  default     = 2048
}

variable "frontend_cpu" {
  description = "Web task CPU units (1024 = 1 vCPU)."
  type        = number
  default     = 256
}

variable "frontend_memory" {
  description = "Web task memory (MiB)."
  type        = number
  default     = 512
}

variable "container_insights" {
  description = "Per-task CloudWatch metrics for the cluster (billed per metric)."
  type        = bool
  default     = false
}

variable "backend_count" {
  description = "API tasks to keep running at least (spread over the zones). Autoscaling adds more under load."
  type        = number
  default     = 2
  validation {
    condition     = var.backend_count >= 2
    error_message = "Run at least two API tasks so one can fail or be replaced without downtime."
  }
}

variable "backend_max_count" {
  description = "Most API tasks autoscaling may run."
  type        = number
  default     = 6
}

variable "frontend_count" {
  description = "Web tasks to keep running at least (spread over the zones). Autoscaling adds more under load."
  type        = number
  default     = 2
  validation {
    condition     = var.frontend_count >= 2
    error_message = "Run at least two web tasks so one can fail or be replaced without downtime."
  }
}

variable "frontend_max_count" {
  description = "Most web tasks autoscaling may run."
  type        = number
  default     = 6
}

variable "autoscaling_cpu_target" {
  description = "Average CPU (percent) the API and web services are scaled to keep."
  type        = number
  default     = 60
}

variable "db_instance_class" {
  description = "RDS instance class. db.t4g.micro (1 GiB) suits a small user base; move to db.t4g.small or larger as it grows."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_engine_version" {
  description = "PostgreSQL major version on RDS."
  type        = string
  default     = "18"
}

variable "db_multi_az" {
  description = "A synchronous standby database in another zone that takes over automatically (doubles the database cost)."
  type        = bool
  default     = true
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

variable "bootstrap_admin_email" {
  description = "Email of the first super-admin, created at start-up while nobody holds that role. Blank: none."
  type        = string
  default     = ""
}

variable "bootstrap_admin_password" {
  description = "That account's first password; it must be changed at first sign-in. Pass it only for the first deploy, as TF_VAR_bootstrap_admin_password, and drop it once the account exists."
  type        = string
  default     = ""
  sensitive   = true
}
