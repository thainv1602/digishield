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

  # Which tenant a user belongs to. The pre-token Lambda reads this and puts it
  # in the token as `tid`, which is the only thing TenantFilter trusts and what
  # every RLS policy filters on.
  schema {
    name                     = "tenant_id"
    attribute_data_type      = "String"
    mutable                  = true
    developer_only_attribute = false
    required                 = false
    string_attribute_constraints {
      min_length = 36
      max_length = 36
    }
  }

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

  # SRP to sign in, refresh to stay signed in — the two the hosted UI needs, and
  # nothing else. The password flows are deliberately absent: ADMIN_USER_PASSWORD
  # _AUTH was switched on by hand at some point to fetch a token from the CLI, and
  # an apply takes it back off. It hands whoever holds the pool's admin
  # credentials a way to trade any user's password for that user's tokens, which
  # is a large door to leave open for a debugging convenience. Get a token through
  # the hosted UI instead.
  explicit_auth_flows = [
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
  ]

  # Short access tokens, because they are the ceiling on how long a demotion
  # takes to bite. The API validates tokens offline against the pool's JWKS, so
  # it cannot see that Cognito revoked one when the Users screen changed a role;
  # the old role survives until the token expires. Cognito's default hour was
  # that whole hour. Fifteen minutes costs a silent refresh four times an hour —
  # oidc-client-ts renews on `expires_in` with automaticSilentRenew, so nobody
  # sees a login screen for it.
  #
  # The id token matches: it is what the frontend reads `cognito:groups` from to
  # decide which screens to draw, and a stale menu that 403s is worse than a
  # menu that has caught up.
  access_token_validity  = 15
  id_token_validity      = 15
  refresh_token_validity = 30 # days — Cognito's default, and what bounds a session

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }
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

# ── User directory: what the API needs to run the Users screen ──────────────
# Adding a user there creates the pool account (Cognito mails the temporary
# password) and puts it in the role's group above; changing a role moves the
# account between those groups, which is why the list/remove actions are here —
# see the app's CognitoUserDirectory, enabled by auth.cognito.directory.enabled
# in the chart.
# The policy is created but attached to nothing: the app's principal differs per
# deployment (an IRSA role on EKS, the IAM user behind the credentials Secret on
# the Jetson k3s cluster, which has no IRSA). Attach it to whichever applies.
data "aws_iam_policy_document" "user_directory" {
  statement {
    actions = [
      "cognito-idp:AdminCreateUser",
      "cognito-idp:AdminGetUser",
      "cognito-idp:AdminAddUserToGroup",
      "cognito-idp:AdminListGroupsForUser",
      "cognito-idp:AdminRemoveUserFromGroup",
      # Ends the sessions of a user whose role just changed, so a demotion is not
      # waiting on their refresh token to run out.
      "cognito-idp:AdminUserGlobalSignOut",
      # Removing a user has to remove the account too, or they keep signing in
      # after they have disappeared from every screen. This is the one action
      # here that destroys something: a compromised app credential can wipe pool
      # accounts, which is the price of the Users screen telling the truth.
      "cognito-idp:AdminDeleteUser",
      # Locking a learner out for missing mandatory training, and letting them
      # back in. Enable is as necessary as disable: without it a suspension has
      # no exit, since the person cannot reach the training that caused it.
      "cognito-idp:AdminDisableUser",
      "cognito-idp:AdminEnableUser",
    ]
    # Scoped to this pool: the app administers its own users and nothing else.
    resources = [aws_cognito_user_pool.main.arn]
  }
}

resource "aws_iam_policy" "user_directory" {
  name        = "${var.name}-user-directory"
  description = "Lets the DigiShield API create and group users in the ${var.name} pool"
  policy      = data.aws_iam_policy_document.user_directory.json
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
      // Cognito pre-token-generation V2_0 trigger.
      //
      // `tid` goes on both tokens so the backend's TenantFilter can resolve the
      // tenant. `email` goes on the ACCESS token because the backend only ever
      // sees that one, and Cognito does not put email there by default -- without
      // it every audit entry is attributed to the `sub` UUID, which answers
      // "who did this" with another question.
      //
      // The tenant comes from the user's own custom:tenant_id attribute. There is
      // deliberately no fallback: a user with no tenant gets no token. Defaulting
      // to any tenant would drop a misconfigured account into somebody else's
      // organisation, and because every table is filtered by RLS on this claim,
      // they would read that organisation's data.
      exports.handler = async (event) => {
        const attrs = event.request.userAttributes || {};
        const tid = attrs['custom:tenant_id'];
        if (!tid) {
          throw new Error(
            'No custom:tenant_id on user ' + (event.userName || '?') +
            ' -- refusing to issue a token rather than guess a tenant');
        }
        const email = attrs.email;
        const accessClaims = email ? { tid, email } : { tid };
        event.response = {
          claimsAndScopeOverrideDetails: {
            idTokenGeneration: { claimsToAddOrOverride: { tid } },
            accessTokenGeneration: { claimsToAddOrOverride: accessClaims },
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
}

# Allow the user pool to invoke the trigger.
resource "aws_lambda_permission" "cognito_invoke_pre_token" {
  statement_id  = "AllowCognitoInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.pre_token.function_name
  principal     = "cognito-idp.amazonaws.com"
  source_arn    = aws_cognito_user_pool.main.arn
}
