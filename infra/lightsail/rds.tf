# The app's database: Amazon RDS PostgreSQL. Its disk grows by itself (up to rds_max_storage_gb) and can be resized or
# moved to a bigger instance without rebuilding the database. (It replaced a Lightsail database in November 2026; that
# database's final snapshot is sealease-dev-db-final.)
#
# The Lightsail server reaches it through Lightsail's VPC peering with the region's default VPC, so the database sits
# in the default VPC and accepts connections only from the Lightsail network, never from the internet.

variable "rds_instance_class" {
  description = "db.t4g.small: 2 GB RAM, 2 vCPU (burstable). db.t4g.medium has 4 GB. Changing it restarts the database (a few minutes)."
  type        = string
  default     = "db.t4g.small"
}

variable "rds_storage_gb" {
  description = "Disk to start with; RDS grows it by itself when it runs low, up to rds_max_storage_gb."
  type        = number
  default     = 20
}

variable "rds_max_storage_gb" {
  type    = number
  default = 200
}

variable "rds_multi_az" {
  description = "A standby copy in another zone with automatic failover. About doubles the database's price."
  type        = bool
  default     = false
}

variable "lightsail_cidr" {
  description = "Lightsail's private network in the region (the server's private address is in it)."
  type        = string
  default     = "172.26.0.0/16"
}

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
  filter {
    name   = "default-for-az"
    values = ["true"]
  }
}

# Lightsail peers with the default VPC once per region; Terraform has no resource for it, so the AWS CLI does it.
resource "terraform_data" "lightsail_peering" {
  input = var.region
  provisioner "local-exec" {
    command = "aws lightsail is-vpc-peered --region ${self.input} --query isPeered --output text | grep -qx True || aws lightsail peer-vpc --region ${self.input} >/dev/null"
  }
}

resource "aws_security_group" "rds" {
  name        = "${local.name}-dev-rds"
  description = "PostgreSQL from the Lightsail server only"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "PostgreSQL from Lightsail"
    protocol    = "tcp"
    from_port   = 5432
    to_port     = 5432
    cidr_blocks = [var.lightsail_cidr]
  }
}

resource "aws_db_subnet_group" "rds" {
  name       = "${local.name}-dev-rds"
  subnet_ids = data.aws_subnets.default.ids
}

# The database's own settings. Connections must use TLS: RDS PostgreSQL 15 and later already sets rds.force_ssl = 1 by
# default (setting it here again only makes Terraform report a change on every plan), as the backend's sslmode=require
# expects.
resource "aws_db_parameter_group" "rds" {
  name   = "${local.name}-dev-postgres18"
  family = "postgres18"
}

resource "random_password" "rds" {
  length  = 32
  special = false
}

resource "aws_db_instance" "main" {
  identifier             = "${local.name}-dev"
  engine                 = "postgres"
  engine_version         = "18.6"
  instance_class         = var.rds_instance_class
  allocated_storage      = var.rds_storage_gb
  max_allocated_storage  = var.rds_max_storage_gb
  storage_type           = "gp3"
  storage_encrypted      = true
  db_name                = "sealease"
  username               = "sealease"
  password               = random_password.rds.result
  db_subnet_group_name   = aws_db_subnet_group.rds.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.rds.name
  publicly_accessible    = false
  multi_az               = var.rds_multi_az
  # Next to the server while there is no standby.
  availability_zone = var.rds_multi_az ? null : var.zone

  backup_retention_period    = 14
  backup_window              = "19:30-20:00"         # 01:00-01:30 IST, before the nightly dump at 02:00 IST
  maintenance_window         = "sun:21:30-sun:22:00" # Monday 03:00-03:30 IST
  copy_tags_to_snapshot      = true
  auto_minor_version_upgrade = true
  deletion_protection        = true
  skip_final_snapshot        = false
  final_snapshot_identifier  = "${local.name}-dev-final"

  depends_on = [terraform_data.lightsail_peering]
}

# env.sh writes these into the API's settings.
output "database_url" {
  value = "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/sealease?sslmode=require"
}

output "database_password" {
  value     = random_password.rds.result
  sensitive = true
}
