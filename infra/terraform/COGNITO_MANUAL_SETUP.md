# Cognito Manual Setup (direct sign-in)

Manual, CLI-based alternative to `cognito.tf` when you want **only** the Cognito
user pool and don't want to `terraform apply` the whole stack (CloudFront / EKS /
RDS). This is the right path for the **Jetson k3s deployment**: the app runs
on-prem (Postgres/Redis/RabbitMQ in-cluster, Traefik ingress), so Cognito is the
only AWS resource still needed — everything else in `infra/terraform/` (EKS, RDS,
ElastiCache, CloudFront, ALB) does **not** apply. See "Jetson notes" at the end.

This reproduces exactly what `cognito.tf` provisions:

- User pool with **email** sign-in, **Essentials** feature plan.
- Public SPA app client (authorization code + PKCE, `COGNITO` IdP only — direct
  sign-in, 10k MAU free tier).
- Hosted UI domain.
- The 6 RBAC role groups → surfaced in the `cognito:groups` claim.
- A **pre-token-generation Lambda (V2)** that injects the `tid` claim the backend
  (`TenantFilter`) requires, into both the id and access tokens.

> **Why Essentials + V2:** the SPA sends the **access token** to the API, and only
> a V2 pre-token-generation trigger can customise the access token — which in turn
> requires the Essentials (or Plus) feature plan. Essentials has the same 10k MAU
> free tier as Lite for direct sign-in, so there is no extra cost at dev scale.

Prerequisites: AWS CLI v2, credentials with Cognito/Lambda/IAM permissions.

## 0. Variables

```bash
export AWS_REGION=ap-southeast-1                          # your region
export TENANT_ID=11111111-1111-1111-1111-111111111111    # = dev_tenant_id (seeded demo tenant)
export POOL_NAME=digishield-users
export ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# Public HTTPS URL where the frontend is reachable (the Jetson ingress hostname).
# MUST be a stable exact HTTPS origin — Cognito does NOT allow wildcards, and only
# http://localhost is exempt from the HTTPS requirement. See "Jetson notes".
export APP_URL="https://digishield.duckdns.org"
```

The `tid` **must** match a `tenant_id` present in the database (RLS filters on it).
The default above is the seeded demo tenant (`DemoTenants.DEMO_TENANT_ID`).

## 1. User pool (email login, Essentials plan)

```bash
POOL_ID=$(aws cognito-idp create-user-pool \
  --pool-name "$POOL_NAME" \
  --user-pool-tier ESSENTIALS \
  --username-attributes email \
  --auto-verified-attributes email \
  --policies 'PasswordPolicy={MinimumLength=8,RequireUppercase=true,RequireLowercase=true,RequireNumbers=true,RequireSymbols=false}' \
  --account-recovery-setting 'RecoveryMechanisms=[{Name=verified_email,Priority=1}]' \
  --query 'UserPool.Id' --output text)
echo "POOL_ID=$POOL_ID"
```

## 2. App client (public SPA, code + PKCE)

```bash
CLIENT_ID=$(aws cognito-idp create-user-pool-client \
  --user-pool-id "$POOL_ID" \
  --client-name digishield-spa \
  --no-generate-secret \
  --allowed-o-auth-flows-user-pool-client \
  --allowed-o-auth-flows code \
  --allowed-o-auth-scopes openid email profile \
  --supported-identity-providers COGNITO \
  --callback-urls "[\"http://localhost:5173\",\"$APP_URL\"]" \
  --logout-urls "[\"http://localhost:5173\",\"$APP_URL\"]" \
  --explicit-auth-flows ALLOW_USER_SRP_AUTH ALLOW_REFRESH_TOKEN_AUTH \
  --query 'UserPoolClient.ClientId' --output text)
echo "CLIENT_ID=$CLIENT_ID"
```

Both `http://localhost:5173` (local dev) and `$APP_URL` (the Jetson public URL)
are registered. Every origin must be listed **exactly** — Cognito rejects
wildcards, and every non-localhost URL must be HTTPS. Add more later with
`update-user-pool-client` (re-passing the full list, it replaces).

## 3. Hosted UI domain

```bash
aws cognito-idp create-user-pool-domain \
  --user-pool-id "$POOL_ID" \
  --domain "digishield-$ACCOUNT_ID"
# -> https://digishield-$ACCOUNT_ID.auth.$AWS_REGION.amazoncognito.com
```

## 4. Role groups (all 6 RBAC roles)

```bash
for g in super_admin org_admin manager content_editor analyst learner; do
  aws cognito-idp create-group --user-pool-id "$POOL_ID" --group-name "$g"
done
```

## 5. Pre-token-generation Lambda (injects `tid`)

### 5a. Code + zip

```bash
mkdir -p /tmp/pretoken && cat > /tmp/pretoken/index.js <<'JS'
'use strict';
// Cognito pre-token-generation V2_0 trigger. Adds `tid` to both the ID and
// access tokens so the backend's TenantFilter can resolve the tenant.
exports.handler = async (event) => {
  const tid = process.env.FIXED_TENANT_ID;
  event.response = {
    claimsAndScopeOverrideDetails: {
      idTokenGeneration:     { claimsToAddOrOverride: { tid } },
      accessTokenGeneration: { claimsToAddOrOverride: { tid } },
    },
  };
  return event;
};
JS
(cd /tmp/pretoken && zip -q pretoken.zip index.js)
```

### 5b. IAM role

```bash
ROLE_ARN=$(aws iam create-role --role-name digishield-cognito-pretoken \
  --assume-role-policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"lambda.amazonaws.com"},"Action":"sts:AssumeRole"}]}' \
  --query 'Role.Arn' --output text)
aws iam attach-role-policy --role-name digishield-cognito-pretoken \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole
sleep 10   # wait for the new role to propagate before Lambda uses it
```

### 5c. Deploy the function

```bash
FN_ARN=$(aws lambda create-function \
  --function-name digishield-cognito-pretoken \
  --runtime nodejs20.x --handler index.handler \
  --role "$ROLE_ARN" \
  --zip-file fileb:///tmp/pretoken/pretoken.zip \
  --environment "Variables={FIXED_TENANT_ID=$TENANT_ID}" \
  --query 'FunctionArn' --output text)
```

### 5d. Allow Cognito to invoke it

```bash
POOL_ARN="arn:aws:cognito-idp:$AWS_REGION:$ACCOUNT_ID:userpool/$POOL_ID"
aws lambda add-permission --function-name digishield-cognito-pretoken \
  --statement-id AllowCognitoInvoke --action lambda:InvokeFunction \
  --principal cognito-idp.amazonaws.com --source-arn "$POOL_ARN"
```

## 6. Attach the V2 trigger to the pool

> ⚠️ `update-user-pool` via CLI **overwrites** the whole config — it can silently
> reset the password policy, verified attributes, domain, etc. **Prefer the
> Console** for this one step:
>
> Cognito → your user pool → **User pool properties** → **Lambda triggers** →
> **Pre token generation** → select `digishield-cognito-pretoken`,
> **Trigger version: v2.0** → Save.

CLI alternative (must re-pass the fields you set in step 1, or they reset):

```bash
aws cognito-idp update-user-pool --user-pool-id "$POOL_ID" \
  --auto-verified-attributes email \
  --policies 'PasswordPolicy={MinimumLength=8,RequireUppercase=true,RequireLowercase=true,RequireNumbers=true,RequireSymbols=false}' \
  --account-recovery-setting 'RecoveryMechanisms=[{Name=verified_email,Priority=1}]' \
  --user-pool-tier ESSENTIALS \
  --lambda-config "PreTokenGenerationConfig={LambdaArn=$FN_ARN,LambdaVersion=V2_0}"
```

## 7. Create a user and assign a role

```bash
aws cognito-idp admin-create-user --user-pool-id "$POOL_ID" \
  --username admin@coquan.gov.vn \
  --user-attributes Name=email,Value=admin@coquan.gov.vn Name=email_verified,Value=true \
  --message-action SUPPRESS
aws cognito-idp admin-set-user-password --user-pool-id "$POOL_ID" \
  --username admin@coquan.gov.vn --password 'ChangeMe#2026' --permanent
aws cognito-idp admin-add-user-to-group --user-pool-id "$POOL_ID" \
  --username admin@coquan.gov.vn --group-name org_admin
```

## 8. Wire into the app

```bash
ISSUER="https://cognito-idp.$AWS_REGION.amazonaws.com/$POOL_ID"
echo "Backend:  AUTH_JWT_ISSUER_URI=$ISSUER"
echo "          AUTH_JWT_AUDIENCE=$CLIENT_ID   # optional"
echo "Frontend: VITE_COGNITO_AUTHORITY=$ISSUER"
echo "          VITE_COGNITO_CLIENT_ID=$CLIENT_ID"
echo "          VITE_COGNITO_REDIRECT_URI=$APP_URL"
echo "          VITE_TENANT_ID=$TENANT_ID"
```

- **Backend (Jetson pods):** set `AUTH_JWT_ISSUER_URI` — this switches the resource
  server on and validates every token's signature/issuer/expiry. (Blank in a
  non-dev profile locks the API down to actuator only.) Inject it via the Helm
  values / a Secret in the k3s cluster, not the AWS stack.
- **Frontend (at build time):** the four `VITE_*` vars are baked into the static
  bundle when you build the image, so rebuild after changing them. `cognitoEnabled`
  turns true → the login screen shows the real Cognito button. `VITE_COGNITO_REDIRECT_URI`
  must equal `$APP_URL` and be one of the registered callback URLs. `VITE_TENANT_ID`
  must equal the Lambda's `tid` so the FE's tenant matches the token.

## 9. Verify `tid` is in the token

Sign in through the hosted UI, grab the **access token**, and decode the payload:

```bash
# paste the JWT into $TOKEN
echo "$TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null | python3 -m json.tool | grep -E 'tid|cognito:groups'
```

Expect `"tid": "1111...1111"` and `"cognito:groups": ["org_admin"]`. If `tid` is
missing, the pool is on the Lite plan or the trigger is V1 (id-token only) — the
access token only gets custom claims from a **V2** trigger on **Essentials/Plus**.

## Cost

$0 at dev scale — Cognito under 10k MAU (direct sign-in), Lambda within the 1M
free requests/month, Essentials plan.

## Teardown

```bash
aws cognito-idp delete-user-pool-domain --user-pool-id "$POOL_ID" --domain "digishield-$ACCOUNT_ID"
aws cognito-idp delete-user-pool --user-pool-id "$POOL_ID"
aws lambda delete-function --function-name digishield-cognito-pretoken
aws iam detach-role-policy --role-name digishield-cognito-pretoken \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole
aws iam delete-role --role-name digishield-cognito-pretoken
```

## Jetson notes

On the Jetson k3s deployment **Cognito is the only AWS resource** — the app,
database, cache and ingress all run on-prem. Two things to get right:

- **A stable, exact, HTTPS callback URL.** Cognito's hosted UI redirects the
  browser back to a URL that must be registered **exactly** (no wildcards) and be
  HTTPS (only `http://localhost` is exempt). The Jetson stack is LAN-only by
  default (`values-jetson.yaml` → `ingress.host: ""`), so first give the frontend
  a stable public HTTPS name, then set `APP_URL` to it and register it (step 2):
  - **DuckDNS + Let's Encrypt on Traefik** (the `JETSON_SETUP.md` "expose later"
    path) → e.g. `https://digishield.duckdns.org`. Stable — best fit.
  - **Tailscale** → `https://<host>.<tailnet>.ts.net`. Stable per-device HTTPS.
  - **Cloudflare *quick* tunnel** (`*.trycloudflare.com`) → the hostname changes
    every run, so you'd have to re-register the callback each time. Only workable
    with a **named** tunnel bound to a fixed custom domain.
  > The backend's Spring CORS (`values-jetson.yaml` → `cors.origins`) *does* accept
  > `*.ts.net` / `*.trycloudflare.com` patterns — but that is a different mechanism
  > from Cognito callback URLs, which never accept wildcards.
- **Wire it into the cluster, not AWS.** `AUTH_JWT_ISSUER_URI` and the built-in
  `VITE_*` values live in the Helm release / a k3s Secret. Nothing in
  `infra/terraform/` besides `cognito.tf` needs to be applied.

## Multi-tenant note

The `tid` here is **fixed** (single tenant, dev). For real multi-tenant, add a
per-user custom attribute (e.g. `custom:tenant_id`), populate it at user creation,
and change the Lambda to read `event.request.userAttributes['custom:tenant_id']`
instead of the `FIXED_TENANT_ID` env var.
