# Cognito for the full AWS stack — reuses the shared ./modules/cognito module.
# For the Jetson (Cognito-only) deployment, apply the standalone ./cognito root
# instead; it uses the same module without the CloudFront dependency.

module "cognito" {
  source        = "./modules/cognito"
  name          = local.name
  dev_tenant_id = var.dev_tenant_id

  callback_urls = [
    "https://${aws_cloudfront_distribution.frontend.domain_name}",
    "http://localhost:5173",
  ]
  logout_urls = [
    "https://${aws_cloudfront_distribution.frontend.domain_name}",
    "http://localhost:5173",
  ]
}

output "cognito_issuer_uri" {
  description = "Set as the app's spring.security.oauth2.resourceserver.jwt.issuer-uri."
  value       = module.cognito.issuer_uri
}

output "cognito_user_pool_id" {
  value = module.cognito.user_pool_id
}

output "cognito_spa_client_id" {
  value = module.cognito.spa_client_id
}

output "cognito_hosted_ui_domain" {
  value = module.cognito.hosted_ui_domain
}
