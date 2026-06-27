variable "environment" {
  description = "Environment name (dev | prod). Used in resource names and the Secrets Manager prefix digishield/<env>."
  type        = string
}

variable "aws_region" {
  description = "AWS region."
  type        = string
  default     = "ap-southeast-1"
}

variable "vpc_cidr" {
  description = "CIDR for the VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "az_count" {
  description = "Number of availability zones to spread subnets across."
  type        = number
  default     = 2
}

variable "eks_version" {
  description = "EKS Kubernetes version."
  type        = string
  default     = "1.30"
}

variable "node_instance_types" {
  description = "Instance types for the EKS managed node group."
  type        = list(string)
  default     = ["t3.large"]
}

variable "node_min_size" {
  type    = number
  default = 1
}
variable "node_max_size" {
  type    = number
  default = 4
}
variable "node_desired_size" {
  type    = number
  default = 2
}

# --- RDS PostgreSQL ---
variable "db_engine_version" {
  type    = string
  default = "16.4"
}
variable "db_instance_class" {
  type    = string
  default = "db.t3.medium"
}
variable "db_allocated_storage" {
  type    = number
  default = 20
}
variable "db_multi_az" {
  type    = bool
  default = false
}
variable "db_name" {
  type    = string
  default = "digishield"
}
variable "db_username" {
  type    = string
  default = "digishield"
}

# --- ElastiCache Redis ---
variable "redis_engine_version" {
  type    = string
  default = "7.1"
}
variable "redis_node_type" {
  type    = string
  default = "cache.t3.micro"
}
variable "redis_num_node_groups" {
  type    = number
  default = 1
}
variable "redis_replicas_per_node_group" {
  type    = number
  default = 1
}

# --- Frontend CDN (S3 + CloudFront) ---
variable "cloudfront_price_class" {
  description = "CloudFront price class. PriceClass_100 = US/EU/Canada (cheapest); _200 adds Asia; _All is global."
  type        = string
  default     = "PriceClass_100"
}

variable "frontend_domain_aliases" {
  description = "Custom domains for the SPA (e.g. [\"app.digishield.vn\"]). Empty = serve on the default *.cloudfront.net domain."
  type        = list(string)
  default     = []
}

variable "frontend_acm_certificate_arn" {
  description = "ACM cert ARN for the custom domains. MUST be in us-east-1 (CloudFront requirement). Required when frontend_domain_aliases is set; empty otherwise."
  type        = string
  default     = ""
}

variable "backend_api_origin_domain" {
  description = "Public DNS of the backend API (the ALB created by the Helm Ingress, or a custom domain in front of it). When set, CloudFront routes /api/* to it so the SPA calls the API same-origin (no CORS). Empty = S3-only; the SPA must then call the API on a full cross-origin URL."
  type        = string
  default     = ""
}

variable "backend_api_origin_protocol_policy" {
  description = "How CloudFront connects to the API origin: https-only (custom domain + ACM on the ALB) or http-only (raw *.elb.amazonaws.com has no public cert)."
  type        = string
  default     = "https-only"
}

variable "github_repository" {
  description = "owner/repo allowed to assume the CI deploy role via OIDC."
  type        = string
  default     = "vuthainguyen1602/digishield"
}

variable "tags" {
  description = "Extra tags applied to all resources."
  type        = map(string)
  default     = {}
}
