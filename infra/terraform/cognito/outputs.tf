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
  description = "AUTH_COGNITO_USER_POOL_ID / auth.cognito.userPoolId in the chart."
  value       = module.cognito.user_pool_id
}

output "cognito_user_directory_policy_arn" {
  description = "Attach to the IAM user whose keys the app runs with, so the Users screen can create sign-in accounts."
  value       = module.cognito.user_directory_policy_arn
}
