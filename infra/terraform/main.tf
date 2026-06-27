provider "aws" {
  region = var.aws_region
  default_tags {
    tags = merge({
      Project     = "digishield"
      Environment = var.environment
      ManagedBy   = "terraform"
    }, var.tags)
  }
}

data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}
data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name        = "digishield-${var.environment}"
  account_id  = data.aws_caller_identity.current.account_id
  partition   = data.aws_partition.current.partition
  azs         = slice(data.aws_availability_zones.available.names, 0, var.az_count)
  secret_path = "digishield/${var.environment}" # matches Helm externalSecrets.remotePrefix
}
