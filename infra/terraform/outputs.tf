output "region" {
  value = var.aws_region
}

output "eks_cluster_name" {
  description = "Set as the GitHub Actions variable EKS_CLUSTER_NAME."
  value       = module.eks.cluster_name
}

output "github_deploy_role_arn" {
  description = "Set as the GitHub Actions secret AWS_DEPLOY_ROLE_ARN."
  value       = aws_iam_role.github_deploy.arn
}

output "external_secrets_irsa_role_arn" {
  description = "Annotate the external-secrets ServiceAccount: eks.amazonaws.com/role-arn=<this>."
  value       = aws_iam_role.external_secrets.arn
}

output "rds_endpoint" {
  description = "Put into values-<env>.yaml database.url (jdbc:postgresql://<host>:5432/digishield)."
  value       = aws_db_instance.this.address
}

output "redis_endpoint" {
  description = "Put into values-<env>.yaml redis.host."
  value       = aws_elasticache_replication_group.this.primary_endpoint_address
}

output "secrets_manager_prefix" {
  description = "Matches Helm externalSecrets.remotePrefix."
  value       = local.secret_path
}
