terraform {
  required_version = ">= 1.9"
  required_providers {
    aws    = { source = "hashicorp/aws", version = "~> 6.0" }
    random = { source = "hashicorp/random", version = "~> 3.6" }
    tls    = { source = "hashicorp/tls", version = "~> 4.0" }
  }
  # State is kept locally in infra/terraform.tfstate (git-ignored). It holds the generated secrets:
  # keep it safe, and back it up (or move it to an S3 backend) before anyone else runs this.
}

provider "aws" {
  region = var.region
  default_tags {
    tags = { Project = "sealease", ManagedBy = "terraform" }
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name     = "sealease"
  site     = "www.${var.domain}"
  site_url = "https://www.${var.domain}"
  azs      = slice(data.aws_availability_zones.available.names, 0, 2)
}
