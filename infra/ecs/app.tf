# ---------------------------------------------------------------- images
resource "aws_ecr_repository" "app" {
  for_each             = toset(["backend", "frontend"])
  name                 = "${local.name}/${each.key}"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  for_each   = aws_ecr_repository.app
  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the last 20 images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 20 }
      action       = { type = "expire" }
    }]
  })
}

# ---------------------------------------------------------------- cluster
resource "aws_ecs_cluster" "main" {
  name = local.name
  # Per-task CPU, memory and restart metrics in CloudWatch, to see a failing task or zone.
  setting {
    name  = "containerInsights"
    value = var.container_insights ? "enabled" : "disabled"
  }
}

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name       = aws_ecs_cluster.main.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]
}

# The first task of each service on regular Fargate (so one always survives Spot reclaims), the rest on Spot if enabled.
locals {
  capacity = var.fargate_spot ? [
    { provider = "FARGATE", base = 1, weight = 0 },
    { provider = "FARGATE_SPOT", base = 0, weight = 1 },
  ] : [{ provider = "FARGATE", base = 0, weight = 1 }]
}

# The web app reaches the API at http://backend:8080 through ECS Service Connect; the API has no public address.
resource "aws_service_discovery_http_namespace" "main" {
  name = local.name
}

resource "aws_cloudwatch_log_group" "app" {
  for_each          = toset(["backend", "frontend"])
  name              = "/sealease/${each.key}"
  retention_in_days = 30
}

data "aws_iam_policy_document" "ecs_tasks" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# Used by ECS to pull images, write logs and read the secrets into the containers.
resource "aws_iam_role" "execution" {
  name               = "${local.name}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "execution_secrets" {
  name = "read-secrets"
  role = aws_iam_role.execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = [aws_secretsmanager_secret.backend.arn]
    }]
  })
}

# The API's own permissions while it runs (no access keys): reading and writing the documents bucket.
resource "aws_iam_role" "backend_task" {
  name               = "${local.name}-backend-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks.json
}

resource "aws_iam_role_policy" "backend_documents" {
  name = "documents-bucket"
  role = aws_iam_role.backend_task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
        Resource = "${aws_s3_bucket.documents.arn}/*"
      },
      {
        Effect   = "Allow"
        Action   = ["s3:ListBucket"]
        Resource = aws_s3_bucket.documents.arn
      },
    ]
  })
}

locals {
  jwt_issuer   = "https://${var.domain}"
  jwt_audience = "sealease-api"
  backend_secrets = concat([for key in [
    "DB_PASSWORD", "JWT_PRIVATE_KEY_LOCATION", "JWT_PUBLIC_KEY_LOCATION", "APP_CRYPTO_DATA_KEYS",
    "APP_CRYPTO_ACTIVE_KEY_ID", "APP_CRYPTO_FINGERPRINT_KEY", "MAIL_USERNAME", "MAIL_PASSWORD",
    ] : { name = key, valueFrom = "${aws_secretsmanager_secret.backend.arn}:${key}::" }],
    local.bootstrap_admin ? [{
      name      = "BOOTSTRAP_ADMIN_PASSWORD"
      valueFrom = "${aws_secretsmanager_secret.backend.arn}:BOOTSTRAP_ADMIN_PASSWORD::"
  }] : [])
  backend_environment = { for k, v in {
    SPRING_PROFILES_ACTIVE         = "prod"
    DB_URL                         = "jdbc:postgresql://${aws_db_instance.main.address}:5432/sealease?sslmode=require"
    DB_USERNAME                    = aws_db_instance.main.username
    KAFKA_BOOTSTRAP_SERVERS        = aws_msk_cluster.main.bootstrap_brokers_tls
    SPRING_KAFKA_SECURITY_PROTOCOL = "SSL"
    APP_KAFKA_CREATETOPICS         = "true"
    KAFKA_TOPIC_REPLICATION_FACTOR = tostring(local.kafka_replication_factor)
    JWT_ISSUER                     = local.jwt_issuer
    JWT_AUDIENCE                   = local.jwt_audience
    APP_PUBLIC_URL                 = local.site_url
    APP_CORS_ALLOWED_ORIGINS       = local.site_url
    MAIL_HOST                      = "email-smtp.${var.region}.amazonaws.com"
    MAIL_PORT                      = "587"
    MAIL_SMTP_AUTH                 = "true"
    MAIL_SMTP_STARTTLS             = "true"
    APP_NOTIFICATION_EMAIL_ENABLED = "true"
    APP_NOTIFICATION_EMAIL_FROM    = "SeaLease <no-reply@${var.domain}>"
    INVOICE_ISSUER_NAME            = var.invoice_issuer_name
    INVOICE_ISSUER_ADDRESS         = var.invoice_issuer_address
    INVOICE_ISSUER_TAX_ID          = var.invoice_issuer_tax_id
    APP_DOCUMENTS_S3_BUCKET        = aws_s3_bucket.documents.bucket
    APP_DOCUMENTS_S3_REGION        = var.region
    BOOTSTRAP_ADMIN_EMAIL          = local.bootstrap_admin ? var.bootstrap_admin_email : ""
    APP_VERSION                    = var.image_tag
  } : k => v if v != "" }
}

# ---------------------------------------------------------------- API
resource "aws_ecs_task_definition" "backend" {
  family                   = "${local.name}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.backend_cpu
  memory                   = var.backend_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.backend_task.arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }
  container_definitions = jsonencode([{
    name         = "backend"
    image        = "${aws_ecr_repository.app["backend"].repository_url}:${var.image_tag}"
    essential    = true
    portMappings = [{ name = "backend", containerPort = 8080, protocol = "tcp" }]
    environment  = [for k, v in local.backend_environment : { name = k, value = v }]
    secrets      = local.backend_secrets
    healthCheck = {
      # No curl in the image: ask the liveness probe over bash's /dev/tcp (the image's /bin/sh has none).
      command     = ["CMD", "bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health/liveness HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q UP <&3"]
      interval    = 15
      timeout     = 5
      retries     = 3
      startPeriod = 120
    }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.app["backend"].name
        awslogs-region        = var.region
        awslogs-stream-prefix = "backend"
      }
    }
  }])
}

resource "aws_ecs_service" "backend" {
  name            = "backend"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = var.backend_count
  dynamic "capacity_provider_strategy" {
    for_each = local.capacity
    content {
      capacity_provider = capacity_provider_strategy.value.provider
      base              = capacity_provider_strategy.value.base
      weight            = capacity_provider_strategy.value.weight
    }
  }
  # Tasks are spread over the zones' subnets; after a zone recovers, ECS moves tasks back to even the spread.
  availability_zone_rebalancing      = "ENABLED"
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  enable_execute_command             = false
  network_configuration {
    subnets          = local.app_subnets
    security_groups  = [aws_security_group.backend.id]
    assign_public_ip = local.nat_count == 0
  }
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.main.arn
    service {
      port_name = "backend"
      client_alias {
        dns_name = "backend"
        port     = 8080
      }
    }
  }
  depends_on = [aws_ecs_cluster_capacity_providers.main]
  lifecycle {
    # Autoscaling owns the running count once the service exists.
    ignore_changes = [desired_count]
  }
}

# ---------------------------------------------------------------- web app
resource "aws_ecs_task_definition" "frontend" {
  family                   = "${local.name}-frontend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.frontend_cpu
  memory                   = var.frontend_memory
  execution_role_arn       = aws_iam_role.execution.arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }
  container_definitions = jsonencode([{
    name         = "frontend"
    image        = "${aws_ecr_repository.app["frontend"].repository_url}:${var.image_tag}"
    essential    = true
    portMappings = [{ name = "frontend", containerPort = 3000, protocol = "tcp" }]
    environment = [
      { name = "BACKEND_URL", value = "http://backend:8080" },
      { name = "JWT_ISSUER", value = local.jwt_issuer },
      { name = "JWT_AUDIENCE", value = local.jwt_audience },
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.app["frontend"].name
        awslogs-region        = var.region
        awslogs-stream-prefix = "frontend"
      }
    }
  }])
}

resource "aws_ecs_service" "frontend" {
  name            = "frontend"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.frontend.arn
  desired_count   = var.frontend_count
  dynamic "capacity_provider_strategy" {
    for_each = local.capacity
    content {
      capacity_provider = capacity_provider_strategy.value.provider
      base              = capacity_provider_strategy.value.base
      weight            = capacity_provider_strategy.value.weight
    }
  }
  # Tasks are spread over the zones' subnets; after a zone recovers, ECS moves tasks back to even the spread.
  availability_zone_rebalancing      = "ENABLED"
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  health_check_grace_period_seconds  = 60
  network_configuration {
    subnets          = local.app_subnets
    security_groups  = [aws_security_group.frontend.id]
    assign_public_ip = local.nat_count == 0
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.frontend.arn
    container_name   = "frontend"
    container_port   = 3000
  }
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  # A client of the API's Service Connect name.
  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.main.arn
  }
  depends_on = [aws_lb_listener.https, aws_ecs_service.backend, aws_ecs_cluster_capacity_providers.main]
  lifecycle {
    # Autoscaling owns the running count once the service exists.
    ignore_changes = [desired_count]
  }
}

# ---------------------------------------------------------------- autoscaling
# Each service keeps its average CPU near the target, between its minimum (never below two tasks) and maximum.
locals {
  scaling = {
    backend  = { min = var.backend_count, max = var.backend_max_count, service = aws_ecs_service.backend.name }
    frontend = { min = var.frontend_count, max = var.frontend_max_count, service = aws_ecs_service.frontend.name }
  }
}

resource "aws_appautoscaling_target" "app" {
  for_each           = local.scaling
  service_namespace  = "ecs"
  scalable_dimension = "ecs:service:DesiredCount"
  resource_id        = "service/${aws_ecs_cluster.main.name}/${each.value.service}"
  min_capacity       = each.value.min
  max_capacity       = max(each.value.min, each.value.max)
}

resource "aws_appautoscaling_policy" "cpu" {
  for_each           = aws_appautoscaling_target.app
  name               = "${each.key}-cpu"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = each.value.service_namespace
  scalable_dimension = each.value.scalable_dimension
  resource_id        = each.value.resource_id
  target_tracking_scaling_policy_configuration {
    target_value       = var.autoscaling_cpu_target
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}
