# Cognito User Pool module — the OIDC identity provider for the app. The backend
# is an OAuth2 resource server that validates JWTs against this pool's issuer; the
# SPA uses the hosted UI / app client to log users in (direct sign-in only).

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

resource "aws_cognito_user_pool" "main" {
  name = "${var.name}-users"

  # Essentials feature plan: required for V2 pre-token-generation to customise the
  # *access* token (the token the SPA sends to the API). Same 10,000 MAU free tier
  # as Lite for direct sign-in, so no extra cost at dev scale.
  user_pool_tier = "ESSENTIALS"

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  # Inject the tenant id into every issued token as the `tid` claim the backend
  # (TenantFilter) requires. V2_0 so the claim also lands in the access token.
  lambda_config {
    pre_token_generation_config {
      lambda_arn     = aws_lambda_function.pre_token.arn
      lambda_version = "V2_0"
    }
  }

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
  name         = "${var.name}-spa"
  user_pool_id = aws_cognito_user_pool.main.id

  generate_secret = false

  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  allowed_oauth_scopes                 = ["openid", "email", "profile"]
  supported_identity_providers         = ["COGNITO"]

  callback_urls = var.callback_urls
  logout_urls   = var.logout_urls

  explicit_auth_flows = [
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
  ]
}

# Hosted UI domain (Cognito-provided *.auth.<region>.amazoncognito.com).
resource "aws_cognito_user_pool_domain" "main" {
  domain       = "${var.name}-${data.aws_caller_identity.current.account_id}"
  user_pool_id = aws_cognito_user_pool.main.id
}

# Role groups -> surface in the JWT "cognito:groups" claim, mapped to ROLE_*
# authorities by SecurityConfig. One per RBAC role in the app (see ROLES in the
# frontend / Roles constants in the backend); RequireRole guards key off these.
resource "aws_cognito_user_group" "roles" {
  for_each = toset([
    "super_admin",
    "org_admin",
    "manager",
    "content_editor",
    "analyst",
    "learner",
  ])
  name         = each.value
  user_pool_id = aws_cognito_user_pool.main.id
}

# ── Pre-token-generation Lambda: injects the fixed `tid` claim ──────────────
# Dev/single-tenant: every login gets the same tenant id. For real multi-tenant,
# replace the hard-coded value with a per-user custom attribute lookup.

data "archive_file" "pre_token_zip" {
  type        = "zip"
  output_path = "${path.module}/.build/pre-token-gen.zip"

  source {
    filename = "index.js"
    content  = <<-JS
      'use strict';
      // Cognito pre-token-generation V2_0 trigger. Adds `tid` to both the ID and
      // access tokens so the backend's TenantFilter can resolve the tenant.
      exports.handler = async (event) => {
        const tid = process.env.FIXED_TENANT_ID;
        event.response = {
          claimsAndScopeOverrideDetails: {
            idTokenGeneration: { claimsToAddOrOverride: { tid } },
            accessTokenGeneration: { claimsToAddOrOverride: { tid } },
          },
        };
        return event;
      };
    JS
  }
}

resource "aws_iam_role" "pre_token_lambda" {
  name = "${var.name}-cognito-pretoken"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "pre_token_logs" {
  role       = aws_iam_role.pre_token_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# Lets the function ship traces when X-Ray tracing is enabled below.
resource "aws_iam_role_policy_attachment" "pre_token_xray" {
  role       = aws_iam_role.pre_token_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/AWSXRayDaemonWriteAccess"
}

resource "aws_lambda_function" "pre_token" {
  function_name    = "${var.name}-cognito-pretoken"
  role             = aws_iam_role.pre_token_lambda.arn
  handler          = "index.handler"
  runtime          = "nodejs20.x"
  filename         = data.archive_file.pre_token_zip.output_path
  source_code_hash = data.archive_file.pre_token_zip.output_base64sha256

  # X-Ray tracing (Trivy AWS-0066).
  tracing_config {
    mode = "Active"
  }

  environment {
    variables = {
      FIXED_TENANT_ID = var.dev_tenant_id
    }
  }
}

# Allow the user pool to invoke the trigger.
resource "aws_lambda_permission" "cognito_invoke_pre_token" {
  statement_id  = "AllowCognitoInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.pre_token.function_name
  principal     = "cognito-idp.amazonaws.com"
  source_arn    = aws_cognito_user_pool.main.arn
}
