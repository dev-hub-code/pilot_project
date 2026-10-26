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

locals {
  jwt_issuer   = "https://${var.domain}"
  jwt_audience = "sealease-api"
  backend_secrets = [for key in [
    "DB_PASSWORD", "JWT_PRIVATE_KEY_LOCATION", "JWT_PUBLIC_KEY_LOCATION", "APP_CRYPTO_DATA_KEYS",
    "APP_CRYPTO_ACTIVE_KEY_ID", "APP_CRYPTO_FINGERPRINT_KEY", "MAIL_USERNAME", "MAIL_PASSWORD",
  ] : { name = key, valueFrom = "${aws_secretsmanager_secret.backend.arn}:${key}::" }]
  backend_environment = { for k, v in {
    SPRING_PROFILES_ACTIVE         = "prod"
    DB_URL                         = "jdbc:postgresql://${aws_db_instance.main.address}:5432/sealease?sslmode=require"
    DB_USERNAME                    = aws_db_instance.main.username
    KAFKA_BOOTSTRAP_SERVERS        = aws_msk_cluster.main.bootstrap_brokers_tls
    SPRING_KAFKA_SECURITY_PROTOCOL = "SSL"
    APP_KAFKA_CREATETOPICS         = "true"
    KAFKA_TOPIC_REPLICATION_FACTOR = "2"
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
    APP_VERSION                    = var.image_tag
  } : k => v if v != "" }
}

# ---------------------------------------------------------------- API
resource "aws_ecs_task_definition" "backend" {
  family                   = "${local.name}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 1024
  memory                   = 2048
  execution_role_arn       = aws_iam_role.execution.arn
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
      # No curl in the image: ask the liveness probe over bash's /dev/tcp.
      command     = ["CMD-SHELL", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health/liveness HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q UP <&3"]
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
  name                               = "backend"
  cluster                            = aws_ecs_cluster.main.id
  task_definition                    = aws_ecs_task_definition.backend.arn
  desired_count                      = var.backend_count
  launch_type                        = "FARGATE"
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  enable_execute_command             = false
  network_configuration {
    subnets         = aws_subnet.private[*].id
    security_groups = [aws_security_group.backend.id]
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
}

# ---------------------------------------------------------------- web app
resource "aws_ecs_task_definition" "frontend" {
  family                   = "${local.name}-frontend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024
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
  name                               = "frontend"
  cluster                            = aws_ecs_cluster.main.id
  task_definition                    = aws_ecs_task_definition.frontend.arn
  desired_count                      = var.frontend_count
  launch_type                        = "FARGATE"
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200
  health_check_grace_period_seconds  = 60
  network_configuration {
    subnets         = aws_subnet.private[*].id
    security_groups = [aws_security_group.frontend.id]
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
  depends_on = [aws_lb_listener.https, aws_ecs_service.backend]
}
