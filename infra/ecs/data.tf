# ---------------------------------------------------------------- PostgreSQL (RDS)
resource "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "main" {
  identifier             = local.name
  engine                 = "postgres"
  engine_version         = var.db_engine_version
  instance_class         = var.db_instance_class
  allocated_storage      = 20
  max_allocated_storage  = 100
  storage_type           = "gp3"
  storage_encrypted      = true
  db_name                = "sealease"
  username               = "sealease"
  password               = random_password.db.result
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  # Multi-AZ: a synchronous standby in another zone; RDS fails over to it automatically (about a minute) when the
  # primary or its zone fails, and patches the standby first.
  multi_az                     = var.db_multi_az
  backup_retention_period      = 14
  backup_window                = "20:00-20:30"         # 01:30-02:00 IST
  maintenance_window           = "sun:21:00-sun:21:30" # Monday 02:30 IST
  copy_tags_to_snapshot        = true
  auto_minor_version_upgrade   = true
  performance_insights_enabled = true
  deletion_protection          = true
  skip_final_snapshot          = false
  final_snapshot_identifier    = "${local.name}-final"
}

# ---------------------------------------------------------------- Kafka (MSK)
# One broker per zone. Every topic is replicated to up to three brokers and a write needs all but one of them, so a
# broker (or a whole zone) can be lost or patched without losing acknowledged events or stopping writes.
locals {
  kafka_replication_factor = min(3, var.az_count)
  kafka_min_insync         = max(1, local.kafka_replication_factor - 1)
}

resource "aws_kms_key" "kafka" {
  description         = "${local.name} Kafka data at rest"
  enable_key_rotation = true
}

resource "aws_kms_alias" "kafka" {
  name          = "alias/${local.name}-kafka"
  target_key_id = aws_kms_key.kafka.key_id
}

resource "aws_msk_configuration" "main" {
  name           = local.name
  kafka_versions = [var.kafka_version]
  # The application creates its topics (APP_KAFKA_CREATETOPICS).
  server_properties = <<-PROPS
    auto.create.topics.enable=false
    default.replication.factor=${local.kafka_replication_factor}
    min.insync.replicas=${local.kafka_min_insync}
    offsets.topic.replication.factor=${local.kafka_replication_factor}
    transaction.state.log.replication.factor=${local.kafka_replication_factor}
    transaction.state.log.min.isr=${local.kafka_min_insync}
    unclean.leader.election.enable=false
  PROPS
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_msk_cluster" "main" {
  cluster_name           = local.name
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.az_count
  broker_node_group_info {
    instance_type   = var.kafka_instance_type
    client_subnets  = aws_subnet.private[*].id
    security_groups = [aws_security_group.kafka.id]
    storage_info {
      ebs_storage_info {
        volume_size = 20
      }
    }
  }
  configuration_info {
    arn      = aws_msk_configuration.main.arn
    revision = aws_msk_configuration.main.latest_revision
  }
  # TLS between the API and the brokers; reachable only from the API's security group. Data at rest is encrypted
  # with this deployment's own KMS key.
  encryption_info {
    encryption_at_rest_kms_key_arn = aws_kms_key.kafka.arn
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }
  client_authentication {
    unauthenticated = true
  }
  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.kafka.name
      }
    }
  }
}

resource "aws_cloudwatch_log_group" "kafka" {
  name              = "/sealease/kafka"
  retention_in_days = 14
}
