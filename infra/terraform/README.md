# DigiShield infrastructure (Terraform)

Provisions the AWS infrastructure the Helm chart + CD pipeline expect, per
environment (`dev`, `prod`):

- **VPC** (public/private subnets, NAT) — `network.tf`
- **EKS** cluster + managed node group + IRSA OIDC — `eks.tf`
- **RDS PostgreSQL** (+ SG, private) — `rds.tf`
- **ElastiCache Redis** (TLS + auth token, private) — `redis.tf`
- **Secrets Manager** `digishield/<env>/db` and `/redis` — `secrets.tf`
- **IAM**: GitHub Actions OIDC deploy role (`iam_github_oidc.tf`) + IRSA role for
  External Secrets Operator (`iam_irsa.tf`)
- **Frontend CDN**: private S3 bucket + CloudFront (OAC, SPA fallback) for the
  React/Vite SPA — `frontend_cdn.tf`. Set custom domains via
  `frontend_domain_aliases` + `frontend_acm_certificate_arn` (ACM in us-east-1).

> Scaffold — review and run `terraform plan` before `apply`. Placeholders
> (`<ACCOUNT_ID>`, sizes, region, CIDRs) live in `envs/*.tfvars` and `envs/*.hcl`.

## How this fits together (read first)

**The CD pipelines do NOT run Terraform.** `cd.yml` / `frontend-cd.yml` only
deploy the app (build image → `helm upgrade`, `s3 sync` → CloudFront
invalidation) and assume the infrastructure already exists — that is why they are
gated by `DEPLOY_ENABLED`. Provisioning is a **deliberate, manual** step
(infra changes are high-risk and want a reviewed `plan`), so you run Terraform
yourself the first time and whenever infra changes.

End-to-end order for a **dev**-only setup (prod stays off, see the prod gate):

1. **Bootstrap** the remote-state bucket + lock table (once per account).
2. **Provision** dev infra with Terraform (`apply`).
3. **Install cluster add-ons** (External Secrets Operator + AWS Load Balancer
   Controller) and create the ClusterSecretStore.
4. **Wire `terraform output` → GitHub** variables/secret (table below).
5. Set `DEPLOY_ENABLED=true` and push to `main` → CD deploys backend + frontend.
6. **Finish API routing**: feed the ALB DNS into `backend_api_origin_domain` and
   re-apply (CloudFront then routes `/api/*` to the backend).

Prerequisites on your machine: AWS CLI (logged in), `terraform`, `kubectl`,
`helm`. Get the account id with `aws sts get-caller-identity`.

## 1. Bootstrap remote state (once per account)

The S3 state bucket + DynamoDB lock table referenced in `envs/backend-*.hcl`
must exist before `terraform init`. Replace `<ACCOUNT_ID>` in
`envs/backend-dev.hcl` to match, then:

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGION=ap-southeast-1

aws s3 mb "s3://digishield-tfstate-$ACCOUNT_ID" --region "$REGION"
aws s3api put-bucket-versioning \
  --bucket "digishield-tfstate-$ACCOUNT_ID" \
  --versioning-configuration Status=Enabled
aws dynamodb create-table --table-name digishield-tflock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST --region "$REGION"
```

## 2. Provision infrastructure

```bash
cd infra/terraform

# --- dev (also creates the account-global GitHub OIDC provider) ---
terraform init -backend-config=envs/backend-dev.hcl
terraform plan  -var-file=envs/dev.tfvars   # review first
terraform apply -var-file=envs/dev.tfvars

# --- prod (separate state; only when you actually ship prod) ---
terraform init -reconfigure -backend-config=envs/backend-prod.hcl
terraform apply -var-file=envs/prod.tfvars
```

EKS + RDS take ~15–20 min. Secrets Manager `digishield/<env>/db` and `/redis`
are populated automatically (generated passwords) — no manual secret creation.

## 3. Cluster add-ons (after EKS exists)

```bash
aws eks update-kubeconfig --name digishield-dev --region ap-southeast-1

# External Secrets Operator — pulls DB/Redis creds from Secrets Manager.
helm repo add external-secrets https://charts.external-secrets.io
helm upgrade --install external-secrets external-secrets/external-secrets \
  -n external-secrets --create-namespace

# Annotate its ServiceAccount with the IRSA role (terraform output
# external_secrets_irsa_role_arn) so it can read Secrets Manager:
kubectl -n external-secrets annotate serviceaccount external-secrets \
  eks.amazonaws.com/role-arn="$(terraform output -raw external_secrets_irsa_role_arn)" --overwrite

# AWS Load Balancer Controller — REQUIRED for the API Ingress/ALB.
helm repo add eks https://aws.github.io/eks-charts
helm upgrade --install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system --set clusterName=digishield-dev
```

Then create the ClusterSecretStore (below).

## 4. Wire outputs to GitHub Actions

After `apply`, `terraform output` gives everything the app side needs:

| Output | Where it goes |
|--------|---------------|
| `eks_cluster_name` | GitHub Actions **variable** `EKS_CLUSTER_NAME` |
| `region` | GitHub Actions **variable** `AWS_REGION` |
| `github_deploy_role_arn` | GitHub Actions **secret** `AWS_DEPLOY_ROLE_ARN` (repo-level + per Environment) |
| `rds_endpoint` | `deploy/helm/digishield/values-<env>.yaml` → `database.url` |
| `redis_endpoint` | `values-<env>.yaml` → `redis.host` |
| `external_secrets_irsa_role_arn` | annotate the ESO ServiceAccount: `eks.amazonaws.com/role-arn` |
| `secrets_manager_prefix` | matches Helm `externalSecrets.remotePrefix` (`digishield/<env>`) |
| `frontend_bucket` | GitHub Actions **variable** `FRONTEND_BUCKET` (FE CD runs `aws s3 sync frontend/dist s3://<this>`) |
| `frontend_distribution_id` | GitHub Actions **variable** `FRONTEND_DISTRIBUTION_ID` (FE CD invalidates this after sync) |
| `frontend_url` | public SPA URL (custom domain if set, else `*.cloudfront.net`) |

`frontend_bucket` / `frontend_distribution_id` should be set as **per-Environment**
variables (`dev` / `production`), since each env has its own bucket and
distribution. Optionally set `VITE_API_BASE_URL` (defaults to `/api/v1`).

The app image lives in **GHCR** (`ghcr.io/<owner>/digishield/app`), not ECR.
Since the package is private, add a GitHub Actions **secret** `GHCR_PULL_TOKEN` =
a PAT with `read:packages`. `cd.yml` uses it to create the in-cluster
`ghcr-pull` `docker-registry` secret (referenced by the Helm chart's
`imagePullSecrets`) so EKS can pull the image.

## 5. Enable and trigger deploys

Set the repo variable **`DEPLOY_ENABLED=true`** so `cd.yml` runs the real
`helm upgrade` instead of the placeholder, then push to `main` (or re-run the CD
workflows). Backend deploys to EKS and the SPA syncs to S3 + CloudFront.

**Production is double-gated.** The `deploy-prod` jobs (in `cd.yml` and
`frontend-cd.yml`) are skipped entirely unless **`PROD_DEPLOY_ENABLED=true`**,
on top of the `production` GitHub Environment's required-reviewer approval. Leave
`PROD_DEPLOY_ENABLED` unset to run dev only (prod incurs no cost); set it to
`true` **and** approve the gate when you actually want to ship production.

## 6. Finish API routing (SPA -> backend, same-origin)

The SPA calls the API same-origin via `VITE_API_BASE_URL=/api/v1`: CloudFront
routes `/api/*` to the backend, so there is no CORS and one URL per env. Wiring,
after the backend is deployed with `ingress.enabled=true`:

1. The Helm Ingress provisions an internet-facing ALB. Read its DNS:
   `kubectl -n digishield-<env> get ingress digishield-api`.
2. Put that DNS (or a Route53 record fronting it) into the Terraform var
   **`backend_api_origin_domain`** (`envs/<env>.tfvars`) and re-apply — CloudFront
   gains the `/api/*` behavior pointing at it. Use a custom ALB domain + ACM cert
   for `backend_api_origin_protocol_policy = "https-only"` (the default).

Leave `backend_api_origin_domain` empty to skip the `/api/*` behavior; the SPA
must then target a full cross-origin API URL (set GH Actions var
`VITE_API_BASE_URL`) and the backend must send CORS headers.

## ClusterSecretStore (part of step 3, after ESO is installed)

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ClusterSecretStore
metadata:
  name: aws-secretsmanager      # must match values externalSecrets.secretStoreRef.name
spec:
  provider:
    aws:
      service: SecretsManager
      region: <AWS_REGION>
      auth:
        jwt:
          serviceAccountRef:
            name: external-secrets
            namespace: external-secrets
```

## Teardown — destroy dev to stop cost

Infra is codified, so the cheapest "pause" is to destroy dev and re-`apply`
later. Use the helper (it shows the destroy plan, asks for confirmation, empties
the S3 bucket that has no `force_destroy`, then destroys):

```bash
cd infra/terraform
./destroy-dev.sh        # prompts: type "destroy dev" to confirm
```

Or manually:

```bash
terraform plan -destroy -var-file=envs/dev.tfvars   # review first
terraform destroy -var-file=envs/dev.tfvars
```

To decommission **production** use `./destroy-prod.sh` (for teardown only, not
cost-pausing). It additionally disables the RDS `deletion_protection` first,
takes a **final DB snapshot** (`digishield-prod-db-final`, restorable), and
**never** deletes the shared GitHub OIDC provider (owned by dev). Confirm by
typing `destroy production`.

Notes:

- **Irreversible.** Dev RDS uses `skip_final_snapshot=true` → no snapshot, data
  is lost. (Prod has `deletion_protection` + a final snapshot.)
- The S3 frontend bucket has no `force_destroy`; the script empties it first so
  `destroy` doesn't fail.
- The **state bucket + DynamoDB lock table** (bootstrap, outside Terraform) are
  kept. Delete them by hand only if you're abandoning the account.
- Dev sets `create_github_oidc_provider` = true, so destroying dev also removes
  the shared OIDC provider — fine while prod doesn't exist; do **not** destroy
  dev once prod references it.
- Recreate anytime: `terraform apply -var-file=envs/dev.tfvars`.
