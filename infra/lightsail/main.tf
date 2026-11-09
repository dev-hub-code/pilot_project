# The low-cost development setup in Mumbai: one Lightsail server running the site with Docker Compose
# (infra/lightsail/compose.yaml: Caddy for HTTPS, the web app, the API and Kafka) and an Amazon RDS PostgreSQL (rds.tf,
# whose disk grows by itself). About $77 a month: the 8 GB server ($44), the database (about $33 for db.t4g.small and
# 20 GB) and cents of S3 and SES; a budget alert (budget.tf) watches $80. The server keeps no data of its own (the database and documents live elsewhere), so if it
# fails it is simply rebuilt and redeployed. For the high-availability setup see infra/ecs.
#   terraform -chdir=infra/lightsail apply     then infra/lightsail/env.sh, then infra/lightsail/deploy.sh
terraform {
  required_version = ">= 1.9"
  required_providers {
    aws    = { source = "hashicorp/aws", version = "~> 6.0" }
    random = { source = "hashicorp/random", version = "~> 3.6" }
  }
  # State is kept locally in infra/lightsail/terraform.tfstate (git-ignored); it holds the database password and the SMTP and S3 secret keys.
}

provider "aws" {
  region = var.region
  default_tags {
    tags = { Project = "sealease", Environment = "development" }
  }
}

variable "region" {
  type    = string
  default = "ap-south-1"
}

variable "zone" {
  type    = string
  default = "ap-south-1a"
}

variable "instance_bundle" {
  description = "large_3_1: 8 GB RAM, 2 vCPU, 160 GB disk, $44/month. medium_3_1 (4 GB, $24) is the cheapest that still fits; xlarge_3_1 (16 GB, 4 vCPU) is $84."
  type        = string
  default     = "large_3_1"
}

variable "ssh_public_key_file" {
  type    = string
  default = "~/.ssh/sealease_lightsail.pub"
}

variable "ssh_cidrs" {
  description = "Who may SSH to the server (deploys). Narrow it to your own address for more safety, e.g. [\"203.0.113.7/32\"]."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

locals {
  name = "sealease"
}

resource "aws_lightsail_instance" "app" {
  name              = "${local.name}-dev"
  availability_zone = var.zone
  blueprint_id      = "ubuntu_24_04"
  bundle_id         = var.instance_bundle
  # Docker, a swap file as headroom, automatic security updates, and the SSH key for deploys.
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

# Free while attached to the server.
resource "aws_lightsail_static_ip" "app" {
  name = "${local.name}-dev-ip"
}

resource "aws_lightsail_static_ip_attachment" "app" {
  static_ip_name = aws_lightsail_static_ip.app.name
  instance_name  = aws_lightsail_instance.app.name
}

# Only SSH and the web. Kafka has no published port, so it is reachable only inside the server.
resource "aws_lightsail_instance_public_ports" "app" {
  instance_name = aws_lightsail_instance.app.name
  port_info {
    protocol  = "tcp"
    from_port = 22
    to_port   = 22
    cidrs     = var.ssh_cidrs
  }
  port_info {
    protocol   = "tcp"
    from_port  = 80
    to_port    = 80
    cidrs      = ["0.0.0.0/0"]
    ipv6_cidrs = ["::/0"]
  }
  port_info {
    protocol   = "tcp"
    from_port  = 443
    to_port    = 443
    cidrs      = ["0.0.0.0/0"]
    ipv6_cidrs = ["::/0"]
  }
}

output "static_ip" {
  description = "Point the GoDaddy A records for @ and www here."
  value       = aws_lightsail_static_ip.app.ip_address
}

output "ssh" {
  value = "ssh -i ~/.ssh/sealease_lightsail ubuntu@${aws_lightsail_static_ip.app.ip_address}"
}
