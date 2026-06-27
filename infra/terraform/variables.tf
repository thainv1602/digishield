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
