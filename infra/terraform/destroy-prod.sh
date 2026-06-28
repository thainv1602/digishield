#!/usr/bin/env bash
#
# Tear down the DigiShield PRODUCTION environment. DANGEROUS — for decommissioning
# only. Recreate later with: terraform apply -var-file=envs/prod.tfvars
#
# Differs from destroy-dev.sh because prod is protected:
#   - RDS has deletion_protection=true -> we disable it first, else destroy fails.
#   - RDS keeps a FINAL SNAPSHOT (digishield-prod-db-final): data is recoverable.
#   - prod uses create_github_oidc_provider=false, so the shared GitHub OIDC
#     provider (owned by dev) is a data source and is NOT destroyed. This script
#     must never delete it.
#
# Usage:  ./destroy-prod.sh
set -euo pipefail

REGION="${AWS_REGION:-ap-southeast-1}"
DB_ID="digishield-prod-db" # = "${local.name}-db" for environment=prod
cd "$(dirname "$0")"        # -> infra/terraform

echo "==> AWS identity:"
aws sts get-caller-identity --output table

echo "==> terraform init (prod backend)"
terraform init -reconfigure -backend-config=envs/backend-prod.hcl -input=false

echo "==> Plan (what will be destroyed):"
terraform plan -destroy -var-file=envs/prod.tfvars

echo
echo "############################################################"
echo "## You are about to DESTROY PRODUCTION.                    ##"
echo "## A final RDS snapshot ($DB_ID-final) will be taken,      ##"
echo "## but everything else is permanently removed.            ##"
echo "############################################################"
read -r -p 'Type "destroy production" to continue: ' reply
[ "$reply" = "destroy production" ] || {
  echo "Aborted."
  exit 1
}

# RDS deletion_protection must be off before destroy can delete the instance.
echo "==> Disabling RDS deletion protection on $DB_ID"
aws rds modify-db-instance --db-instance-identifier "$DB_ID" \
  --no-deletion-protection --apply-immediately --region "$REGION" >/dev/null 2>&1 || true
echo "==> Waiting for RDS to settle..."
aws rds wait db-instance-available --db-instance-identifier "$DB_ID" --region "$REGION" 2>/dev/null || true

# Empty the prod frontend bucket (no force_destroy). Do NOT touch the OIDC provider.
bucket="$(terraform output -raw frontend_bucket 2>/dev/null || true)"
if [ -n "$bucket" ]; then
  echo "==> Emptying S3 bucket $bucket"
  aws s3 rm "s3://$bucket" --recursive || true
fi

echo "==> Destroying prod infrastructure (final DB snapshot will be created)..."
terraform destroy -var-file=envs/prod.tfvars -auto-approve

echo
echo "==> Done. Production infra removed."
echo "    Final DB snapshot: ${DB_ID}-final (restore from it to recover data)."
echo "    Kept: shared GitHub OIDC provider (owned by dev), state bucket + lock table."
echo "    Recreate with: terraform apply -var-file=envs/prod.tfvars"
