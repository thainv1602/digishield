terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.4"
    }
  }

  # Local state on purpose: this standalone root provisions ONLY Cognito for the
  # on-prem Jetson deployment, so it does not share the full stack's S3 backend.
  # `terraform apply` here creates the user pool, app client, groups and the tid
  # Lambda — nothing else (no CloudFront / EKS / RDS).
}
