variable "aws_region" {
  description = "AWS region for the Cognito user pool."
  type        = string
  default     = "ap-southeast-1"
}

variable "name" {
  description = "Name prefix for the user pool and related resources."
  type        = string
  default     = "digishield-jetson"
}

variable "dev_tenant_id" {
  description = "Fixed tenant UUID injected as the JWT `tid` claim. Must match a tenant_id present in the DB (RLS filters on it). Defaults to the seeded demo tenant."
  type        = string
  default     = "11111111-1111-1111-1111-111111111111"
}

variable "app_url" {
  description = "Public HTTPS URL of the frontend (the Jetson ingress hostname). Must be a stable, exact HTTPS origin — Cognito does not allow wildcards."
  type        = string
}
