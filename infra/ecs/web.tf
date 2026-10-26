# ---------------------------------------------------------------- HTTPS certificate
# Validated by the CNAME records in the godaddy_dns_records output; terraform apply waits until they are in place.
resource "aws_acm_certificate" "site" {
  domain_name               = local.site
  subject_alternative_names = [var.domain]
  validation_method         = "DNS"
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_acm_certificate_validation" "site" {
  certificate_arn = aws_acm_certificate.site.arn
  timeouts {
    create = "2h"
  }
}

# ---------------------------------------------------------------- load balancer
resource "aws_lb" "main" {
  name                       = local.name
  load_balancer_type         = "application"
  subnets                    = aws_subnet.public[*].id
  security_groups            = [aws_security_group.alb.id]
  drop_invalid_header_fields = true
  # Uploads (KYC files, proofs) can take a while on slow connections.
  idle_timeout = 120
}

resource "aws_lb_target_group" "frontend" {
  name                 = "${local.name}-frontend"
  port                 = 3000
  protocol             = "HTTP"
  target_type          = "ip"
  vpc_id               = aws_vpc.main.id
  deregistration_delay = 30
  health_check {
    # A static file: answers without a session or the API.
    path    = "/favicon.ico"
    matcher = "200"
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"
  default_action {
    type = "redirect"
    redirect {
      host        = local.site
      protocol    = "HTTPS"
      port        = "443"
      status_code = "HTTP_301"
    }
  }
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.site.certificate_arn
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.frontend.arn
  }
}

# The bare domain, if it is ever pointed here, goes to www.
resource "aws_lb_listener_rule" "bare_domain" {
  listener_arn = aws_lb_listener.https.arn
  priority     = 10
  condition {
    host_header {
      values = [var.domain]
    }
  }
  action {
    type = "redirect"
    redirect {
      host        = local.site
      status_code = "HTTP_301"
    }
  }
}
