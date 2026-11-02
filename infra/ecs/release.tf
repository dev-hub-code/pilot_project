# ---------------------------------------------------------------- the image that is live
# Every deployment (infra/ecs/deploy.sh, rollout.sh) records its image tag here. Terraform runs whatever it says unless
# -var image_tag is given, so infrastructure changes and app deployments never undo each other.
resource "aws_ssm_parameter" "image_tag" {
  name           = "/${local.name}/image-tag"
  type           = "String"
  insecure_value = var.image_tag != "" ? var.image_tag : "unset"
  lifecycle {
    # Written by the deployments from then on.
    ignore_changes = [insecure_value]
  }
}

locals {
  image_tag = var.image_tag != "" ? var.image_tag : aws_ssm_parameter.image_tag.insecure_value
}
