output "issuer_uri" {
  description = "Set as the app's spring.security.oauth2.resourceserver.jwt.issuer-uri / AUTH_JWT_ISSUER_URI / VITE_COGNITO_AUTHORITY."
  value       = "https://cognito-idp.${data.aws_region.current.name}.amazonaws.com/${aws_cognito_user_pool.main.id}"
}

output "user_pool_id" {
  value = aws_cognito_user_pool.main.id
}

output "spa_client_id" {
  description = "VITE_COGNITO_CLIENT_ID (and optional AUTH_JWT_AUDIENCE)."
  value       = aws_cognito_user_pool_client.spa.id
}

output "hosted_ui_domain" {
  value = "https://${aws_cognito_user_pool_domain.main.domain}.auth.${data.aws_region.current.name}.amazoncognito.com"
}
