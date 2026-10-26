# One Lightsail server running the site with Docker Compose (deploy/compose.yaml) and a managed Lightsail PostgreSQL.
# Sydney: the account's organization policy blocks Mumbai (see infra/ecs for the ECS + RDS + MSK setup).
terraform {
  required_version = ">= 1.9"
  required_providers {
    aws    = { source = "hashicorp/aws", version = "~> 6.0" }
    random = { source = "hashicorp/random", version = "~> 3.6" }
  }
  # State is kept locally in infra/lightsail/terraform.tfstate (git-ignored); it holds the database password.
}

provider "aws" {
  region = var.region
}

variable "region" {
  type    = string
  default = "ap-southeast-2"
}

variable "zone" {
  type    = string
  default = "ap-southeast-2a"
}

variable "instance_bundle" {
  description = "8 GB RAM, 2 vCPU, 160 GB disk."
  type        = string
  default     = "large_3_2"
}

variable "database_bundle" {
  description = "1 GB RAM, 40 GB disk."
  type        = string
  default     = "micro_2_0"
}

variable "ssh_public_key_file" {
  type    = string
  default = "~/.ssh/sealease_lightsail.pub"
}

locals {
  name = "sealease"
}

resource "aws_lightsail_instance" "app" {
  # Lightsail names are shared by all resource types: the database is "sealease".
  name              = "${local.name}-app"
  availability_zone = var.zone
  blueprint_id      = "ubuntu_24_04"
  bundle_id         = var.instance_bundle
  # Docker, a swap file as headroom, and the SSH key for deploys.
  user_data = <<-EOT
    #!/bin/bash
    set -eux
    install -d -m 700 -o ubuntu -g ubuntu /home/ubuntu/.ssh
    echo '${trimspace(file(pathexpand(var.ssh_public_key_file)))}' >> /home/ubuntu/.ssh/authorized_keys
    chown ubuntu:ubuntu /home/ubuntu/.ssh/authorized_keys
    chmod 600 /home/ubuntu/.ssh/authorized_keys
    fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
    export DEBIAN_FRONTEND=noninteractive
    apt-get update
    apt-get install -y docker.io docker-compose-v2 unattended-upgrades
    usermod -aG docker ubuntu
    systemctl enable --now docker
    install -d -o ubuntu -g ubuntu /opt/sealease
  EOT
  lifecycle {
    ignore_changes = [user_data]
  }
}

resource "aws_lightsail_static_ip" "app" {
  name = "${local.name}-ip"
}

resource "aws_lightsail_static_ip_attachment" "app" {
  static_ip_name = aws_lightsail_static_ip.app.name
  instance_name  = aws_lightsail_instance.app.name
}

resource "aws_lightsail_instance_public_ports" "app" {
  instance_name = aws_lightsail_instance.app.name
  port_info {
    protocol  = "tcp"
    from_port = 22
    to_port   = 22
  }
  port_info {
    protocol  = "tcp"
    from_port = 80
    to_port   = 80
  }
  port_info {
    protocol  = "tcp"
    from_port = 443
    to_port   = 443
  }
}

resource "random_password" "db" {
  length  = 32
  special = false
}

# Reachable only from Lightsail resources in the region (not from the internet). Daily automatic backups, kept 7 days.
resource "aws_lightsail_database" "main" {
  relational_database_name     = local.name
  availability_zone            = var.zone
  blueprint_id                 = "postgres_18"
  bundle_id                    = var.database_bundle
  master_database_name         = "sealease"
  master_username              = "sealease"
  master_password              = random_password.db.result
  backup_retention_enabled     = true
  preferred_backup_window      = "20:30-21:00"
  preferred_maintenance_window = "sun:21:30-sun:22:00"
  publicly_accessible          = false
  skip_final_snapshot          = false
  final_snapshot_name          = "${local.name}-final"
}

output "static_ip" {
  description = "Point the GoDaddy A records for @ and www here."
  value       = aws_lightsail_static_ip.app.ip_address
}

output "database_url" {
  value = "jdbc:postgresql://${aws_lightsail_database.main.master_endpoint_address}:${aws_lightsail_database.main.master_endpoint_port}/sealease?sslmode=require"
}

output "database_password" {
  value     = random_password.db.result
  sensitive = true
}
