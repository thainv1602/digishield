# Dev environment runbook (reproducible deploy)

End-to-end order to stand up dev from nothing. Steps 1–2 and 6 are codified
(Terraform / scripts); the rest are one-off operator actions (TLS cert on a free
DuckDNS domain, DNS, Cognito users) that don't belong in Terraform.

Prereqs: `aws` (logged in), `terraform`, `make`, `kubectl`, `helm`, `npm`, and the
acme.sh checkout (or `git clone https://github.com/acmesh-official/acme.sh`).

## 1. Provision infrastructure
Run Terraform through the `make` wrapper (`infra/terraform/Makefile`): it binds the
backend + tfvars to `ENV` and refuses to apply if the initialized backend doesn't
match `ENV` — the guard-rail against applying to the wrong environment.
```bash
cd infra/terraform
make init  ENV=dev     # terraform init -reconfigure with the dev backend
make apply ENV=dev     # terraform apply -var-file=envs/dev.tfvars
```
Creates EKS, RDS, ElastiCache, Secrets Manager, the GitHub OIDC + IRSA roles
(External Secrets **and** the Load Balancer Controller), the SPA S3+CloudFront,
the **Cognito** user pool + role groups, and the static NLB EIPs.

> Free-plan note: `dev.tfvars` uses `m7i-flex.large` (free-tier-eligible) and
> `db.t3.micro`; other types are blocked. RDS engine is pinned to an available
> 16.x. The CloudFront `/api/*` origin is the NLB DNS over HTTP.

## 2. Install cluster add-ons
```bash
./infra/bootstrap-addons.sh
```
Installs External Secrets Operator (+ ClusterSecretStore), the AWS Load Balancer
Controller (with the IRSA role from step 1), and ingress-nginx fronted by an NLB
on the static EIPs. Prints the EIP allocation ids.

## 3. TLS cert (Let's Encrypt via DuckDNS DNS-01)
The app is served over HTTPS on `digishield.duckdns.org`. acme.sh issues the cert
using the DuckDNS token (DNS-01); it is NOT auto-renewed in-cluster — re-run
before the ~90-day expiry.
```bash
export DuckDNS_Token=<your-duckdns-token>
acme.sh --register-account -m <you@example.com> --server letsencrypt
acme.sh --issue --dns dns_duckdns -d digishield.duckdns.org --server letsencrypt
# Load the cert into the app namespace as the secret the ingress references:
kubectl create namespace digishield-dev --dry-run=client -o yaml | kubectl apply -f -
CERT=~/.acme.sh/digishield.duckdns.org_ecc
kubectl -n digishield-dev create secret tls digishield-tls \
  --cert="$CERT/fullchain.cer" --key="$CERT/digishield.duckdns.org.key" \
  --dry-run=client -o yaml | kubectl apply -f -
```

## 4. Point DNS at the NLB
Point the DuckDNS A record at one of the NLB EIPs (printed by step 2):
```bash
curl "https://www.duckdns.org/update?domains=digishield&token=<token>&ip=<an-EIP>"
```

## 5. Cognito users
The role groups (`super_admin`, `org_admin`, `analyst`, `learner`) are created by
Terraform. Add users and put each in a group (drives the `cognito:groups` claim →
the app's role):
```bash
POOL=$(terraform -chdir=infra/terraform output -raw cognito_user_pool_id)
aws cognito-idp admin-create-user --user-pool-id $POOL --username you@example.com \
  --user-attributes Name=email,Value=you@example.com Name=email_verified,Value=true --message-action SUPPRESS
aws cognito-idp admin-set-user-password --user-pool-id $POOL --username you@example.com --password '...' --permanent
aws cognito-idp admin-add-user-to-group --user-pool-id $POOL --username you@example.com --group-name org_admin
```

## 6. Wire CI/CD + deploy
Feed the Terraform outputs to GitHub Actions, then push to `main` (CD builds the
image and runs `helm upgrade`; frontend-cd builds the SPA and syncs S3+CloudFront).
```bash
cd infra/terraform
gh secret  set AWS_DEPLOY_ROLE_ARN      --body "$(terraform output -raw github_deploy_role_arn)"
gh variable set AWS_REGION              --body "$(terraform output -raw region)"
gh variable set EKS_CLUSTER_NAME        --body "$(terraform output -raw eks_cluster_name)"
gh variable set FRONTEND_BUCKET         --body "$(terraform output -raw frontend_bucket)"
gh variable set FRONTEND_DISTRIBUTION_ID --body "$(terraform output -raw frontend_distribution_id)"
gh variable set VITE_API_BASE_URL       --body "/api/v1"
gh variable set VITE_COGNITO_AUTHORITY  --body "$(terraform output -raw cognito_issuer_uri)"
gh variable set VITE_COGNITO_CLIENT_ID  --body "$(terraform output -raw cognito_spa_client_id)"
gh variable set DEPLOY_ENABLED          --body "true"
# GHCR pull token: a PAT with read:packages (the private image registry)
gh secret set GHCR_PULL_TOKEN --body "ghp_..."
```
Also set the backend issuer in `deploy/helm/digishield/values-dev.yaml`
(`auth.issuerUri`) to `terraform output cognito_issuer_uri` if the pool was
recreated. Then merge to `main`.

## Teardown
Delete the NLB-creating add-ons FIRST (so the LBC removes the NLB and frees the
ENIs/EIPs before the VPC is destroyed):
```bash
helm -n ingress-nginx uninstall ingress-nginx   # LBC deletes the NLB
helm -n digishield-dev uninstall digishield
aws s3 rm s3://$(terraform -chdir=infra/terraform output -raw frontend_bucket) --recursive
cd infra/terraform && make destroy ENV=dev
```
The EIPs are Terraform-managed now, so `destroy` releases them. The state bucket
+ lock table are kept.
