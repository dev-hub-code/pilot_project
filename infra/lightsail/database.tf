# The Lightsail managed PostgreSQL the app used first; it is being replaced by RDS (rds.tf). Once migrate-to-rds.sh
# has moved the data and the app runs on RDS, this database can be deleted (a final snapshot is kept).
# Managed PostgreSQL (small_2_0: 2 GB RAM, 80 GB disk, $30/month). Reachable only from Lightsail resources in the
# region (the server), not from the internet. Automatic daily backups kept 7 days with point-in-time restore, and a
# final snapshot if it is ever destroyed; the server also dumps it to S3 every night (backup-db.sh).
resource "random_password" "db" {
  length  = 32
  special = false
}

variable "database_bundle" {
  description = "small_2_0: 2 GB RAM, 80 GB disk, $30/month. Storage comes with the plan: micro_2_0 is 40 GB ($15), medium_2_0 120 GB ($60). A running database cannot change plan; restore a snapshot into a bigger one."
  type        = string
  default     = "small_2_0"
}

resource "aws_lightsail_database" "main" {
  relational_database_name     = "${local.name}-dev-db"
  availability_zone            = var.zone
  blueprint_id                 = "postgres_18"
  bundle_id                    = var.database_bundle
  master_database_name         = "sealease"
  master_username              = "sealease"
  master_password              = random_password.db.result
  backup_retention_enabled     = true
  preferred_backup_window      = "20:00-20:30"
  preferred_maintenance_window = "sun:21:30-sun:22:00"
  publicly_accessible          = false
  apply_immediately            = true
  skip_final_snapshot          = false
  final_snapshot_name          = "${local.name}-dev-db-final"
}

output "lightsail_database_url" {
  value = "jdbc:postgresql://${aws_lightsail_database.main.master_endpoint_address}:${aws_lightsail_database.main.master_endpoint_port}/sealease?sslmode=require"
}

output "lightsail_database_password" {
  value     = random_password.db.result
  sensitive = true
}

# The database the app uses (var.database in rds.tf); env.sh writes these into the API's settings.
output "database_url" {
  value = var.database == "rds" ? "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/sealease?sslmode=require" : "jdbc:postgresql://${aws_lightsail_database.main.master_endpoint_address}:${aws_lightsail_database.main.master_endpoint_port}/sealease?sslmode=require"
}

output "database_password" {
  value     = var.database == "rds" ? random_password.rds.result : random_password.db.result
  sensitive = true
}
