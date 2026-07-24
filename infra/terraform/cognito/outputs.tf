output "cognito_issuer_uri" {
  description = "AUTH_JWT_ISSUER_URI (backend) / VITE_COGNITO_AUTHORITY (frontend)."
  value       = module.cognito.issuer_uri
}

output "cognito_spa_client_id" {
  description = "VITE_COGNITO_CLIENT_ID."
  value       = module.cognito.spa_client_id
}

output "cognito_hosted_ui_domain" {
  value = module.cognito.hosted_ui_domain
}

output "cognito_user_pool_id" {
  value = module.cognito.user_pool_id
}
