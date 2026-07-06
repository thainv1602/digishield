# DigiShield — Dev Deploy Runbook (account-specific values)

This is the account-specific companion to the canonical end-to-end procedure in
[`../RUNBOOK-dev.md`](../RUNBOOK-dev.md). Follow that runbook for the steps
(Terraform → add-ons → TLS cert → DNS → Cognito users → CD); use the tables here
for the concrete values, prerequisites, and cost of **this** account.

> **Scope:** dev only. Production is intentionally deferred to save AWS cost — do
> not run the prod apply or set `PROD_DEPLOY_ENABLED` unless that changes.
>
> The app image lives in **GHCR** (no ECR); EKS pulls it via an in-cluster
> `ghcr-pull` secret that CD creates from `GHCR_PULL_TOKEN`.

## Fixed values for this setup

| Thing | Value |
|---|---|
| AWS account | `743337585084` |
| AWS region | `ap-southeast-1` |
| Terraform state bucket | `digishield-tfstate-743337585084` |
| Terraform lock table | `digishield-tflock` |
| GHCR image | `ghcr.io/vuthainguyen1602/digishield/app` |
| GitHub repo | `vuthainguyen1602/digishield` |
| EKS cluster (dev) | `digishield-dev` |
| Namespace (dev) | `digishield-dev` |
| Public hostname (dev) | `digishield.duckdns.org` (Let's Encrypt via DuckDNS DNS-01) |
| Node instance type | `m7i-flex.large` (free-tier-eligible; t3.large is blocked) |
| RDS class / engine | `db.t3.micro` / PostgreSQL `16.9` (free-tier classes only) |
| Redis node type | `cache.t3.micro`, single node |

## Cost warning

A dev bring-up provisions paid resources: EKS control plane (~$73/mo), a
NAT Gateway (~$32/mo + data), RDS `db.t3.micro`, ElastiCache `cache.t3.micro`,
the ingress-nginx NLB (+ its Elastic IPs), and CloudFront. Tear down when not in
use (state bucket + lock table are kept, ~$0).

## One-time prerequisites (manual, do these first)

These cannot be automated from here:

1. **Create the GHCR pull PAT.** GitHub → Settings → Developer settings →
   Personal access tokens → generate a token with **`read:packages`** that can
   read the `digishield/app` package. It becomes the `GHCR_PULL_TOKEN` secret.
2. **Confirm GHCR package visibility.** `ghcr.io/vuthainguyen1602/digishield/app`
   is kept **private**, so the PAT above is required.
3. **Code-owner approval for `main`.** Merges to `main` require a review from the
   `nvuthai1602` code owner.
4. **Have an acme.sh checkout + DuckDNS token** ready for the TLS cert step
   (`digishield.duckdns.org`).

## The procedure

Follow [`../RUNBOOK-dev.md`](../RUNBOOK-dev.md) in order. In brief:

1. **Bootstrap remote state** (once per account) — create the state bucket + lock
   table above, then set the bucket in `envs/backend-dev.hcl` (replace
   `<ACCOUNT_ID>` with `743337585084`).
2. **Provision dev infra:** `make init ENV=dev && make apply ENV=dev`
   (~15–20 min; **starts billing**). The `make` wrapper (`Makefile`) pins the
   backend + tfvars to `ENV` and blocks an apply if the initialized backend
   doesn't match — the guard against applying to the wrong environment. Creates
   EKS, RDS, ElastiCache, VPC/NAT, S3+CloudFront, Cognito, the GitHub OIDC + IRSA
   roles, and the static NLB EIPs.
3. **Install add-ons:** `./infra/bootstrap-addons.sh` — ESO (+ClusterSecretStore),
   AWS Load Balancer Controller, and ingress-nginx on the NLB/EIPs.
4. **TLS cert + DNS:** issue the Let's Encrypt cert (acme.sh / DuckDNS DNS-01)
   into the `digishield-tls` secret, then point the DuckDNS A record at an NLB EIP.
5. **Cognito users:** add users and put each in a role group.
6. **Wire CD + deploy:** set the GitHub variables/secrets from `terraform output`
   (incl. the Cognito + frontend ones), set `DEPLOY_ENABLED=true`, push to `main`.

## Verify

```bash
kubectl -n digishield-dev get pods,ingress
kubectl -n digishield-dev rollout status deploy/digishield-api
terraform -chdir=infra/terraform output -raw frontend_url   # open the SPA
```

## Teardown (stop cost)

Delete the NLB-creating add-ons first, then destroy (see the README/runbook
teardown sections for the exact order):

```bash
helm -n ingress-nginx uninstall ingress-nginx
cd infra/terraform && ./destroy-dev.sh    # prompts: type "destroy dev"
```

Removes all dev infra (~$0 after). The state bucket + lock table are kept;
recreate anytime by re-running from step 2 + `bootstrap-addons.sh`.
