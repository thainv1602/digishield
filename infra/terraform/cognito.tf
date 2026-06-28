# Cognito User Pool — the OIDC identity provider for the app. The backend is an
# OAuth2 resource server that validates JWTs against this pool's issuer; the SPA
# uses the hosted UI / app client to log users in.

resource "aws_cognito_user_pool" "main" {
  name = "${local.name}-users"

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_numbers   = true
    require_uppercase = true
    require_symbols   = false
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }
}

# Public SPA client (no secret; authorization-code + PKCE).
resource "aws_cognito_user_pool_client" "spa" {
  name         = "${local.name}-spa"
  user_pool_id = aws_cognito_user_pool.main.id

  generate_secret = false

  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  supported_identity_providers         = ["COGNITO"]

  callback_urls = [
    "https://${aws_cloudfront_distribution.frontend.domain_name}",
    "http://localhost:5173",
  ]
  logout_urls = [
    "https://${aws_cloudfront_distribution.frontend.domain_name}",
    "http://localhost:5173",
  ]

  explicit_auth_flows = [
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
  ]
}

# Hosted UI domain (Cognito-provided *.auth.<region>.amazoncognito.com).
resource "aws_cognito_user_pool_domain" "main" {
  domain       = "${local.name}-${local.account_id}"
  user_pool_id = aws_cognito_user_pool.main.id
}

# Role groups -> surface in the JWT "cognito:groups" claim. One per UI persona
# (the app's RequireRole guards key off these role strings).
resource "aws_cognito_user_group" "roles" {
  for_each     = toset(["super_admin", "org_admin", "analyst", "learner"])
  name         = each.value
  user_pool_id = aws_cognito_user_pool.main.id
}

output "cognito_issuer_uri" {
  description = "Set as the app's spring.security.oauth2.resourceserver.jwt.issuer-uri."
  value       = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.main.id}"
}

output "cognito_user_pool_id" {
  value = aws_cognito_user_pool.main.id
}

output "cognito_spa_client_id" {
  value = aws_cognito_user_pool_client.spa.id
}

output "cognito_hosted_ui_domain" {
  value = "https://${aws_cognito_user_pool_domain.main.domain}.auth.${var.aws_region}.amazoncognito.com"
}
