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

## Prerequisites (once per account)

1. An S3 bucket + DynamoDB lock table for remote state (referenced in
   `envs/backend-*.hcl`).
2. Install the **External Secrets Operator** and the **AWS Load Balancer
   Controller** into the cluster after it exists (Helm).

## Usage

```bash
cd infra/terraform

# --- dev (also creates the account-global GitHub OIDC provider) ---
terraform init -backend-config=envs/backend-dev.hcl
terraform apply -var-file=envs/dev.tfvars

# --- prod (separate state) ---
terraform init -reconfigure -backend-config=envs/backend-prod.hcl
terraform apply -var-file=envs/prod.tfvars
```

## Wiring the outputs back

After `apply`, `terraform output` gives everything the app side needs:

| Output | Where it goes |
|--------|---------------|
| `eks_cluster_name` | GitHub Actions **variable** `EKS_CLUSTER_NAME` |
| `region` | GitHub Actions **variable** `AWS_REGION` |
| `github_deploy_role_arn` | GitHub Actions **secret** `AWS_DEPLOY_ROLE_ARN` (repo-level + per Environment) |
| `ecr_repository_url` | GitHub Actions **variable** `ECR_REPOSITORY` (CD pushes/pulls the image here) |
| `rds_endpoint` | `deploy/helm/digishield/values-<env>.yaml` → `database.url` |
| `redis_endpoint` | `values-<env>.yaml` → `redis.host` |
| `external_secrets_irsa_role_arn` | annotate the ESO ServiceAccount: `eks.amazonaws.com/role-arn` |
| `secrets_manager_prefix` | matches Helm `externalSecrets.remotePrefix` (`digishield/<env>`) |
| `frontend_bucket` | GitHub Actions **variable** `FRONTEND_BUCKET` (FE CD runs `aws s3 sync frontend/dist s3://<this>`) |
| `frontend_distribution_id` | GitHub Actions **variable** `FRONTEND_DISTRIBUTION_ID` (FE CD invalidates this after sync) |
| `frontend_url` | public SPA URL (custom domain if set, else `*.cloudfront.net`) |

Then set the repo variable **`DEPLOY_ENABLED=true`** so `cd.yml` runs the real
`helm upgrade` instead of the placeholder.

## API routing (SPA -> backend, same-origin)

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

## Create the ClusterSecretStore (after ESO is installed)

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
