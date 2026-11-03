# Emails a warning before the month's AWS bill passes the budget: at 80% of it (actual spend) and when AWS forecasts
# the month will exceed it. Budgets cost nothing (the first two in an account are free) and never stop anything.
variable "monthly_budget_usd" {
  type    = number
  default = 80
}

variable "budget_email" {
  description = "Who gets the budget warnings."
  type        = string
  default     = "support@sealease.in"
}

resource "aws_budgets_budget" "monthly" {
  name         = "${local.name}-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.budget_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.budget_email]
  }
}
