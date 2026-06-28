#!/usr/bin/env bash
#
# Tear down the DigiShield DEV environment to stop AWS cost.
#
# Destroys everything in the dev Terraform state (EKS, RDS, ElastiCache, NAT,
# ALB, S3 + CloudFront, IAM, Secrets Manager). Recreate later with
#   terraform apply -var-file=envs/dev.tfvars
#
# IRREVERSIBLE: the dev RDS is created with skip_final_snapshot=true, so its data
# is gone with no snapshot. The Terraform *state* bucket + DynamoDB lock table
# (bootstrap resources, outside Terraform) are NOT touched.
#
# Usage:  ./destroy-dev.sh
set -euo pipefail

cd "$(dirname "$0")" # -> infra/terraform

echo "==> AWS identity:"
aws sts get-caller-identity --output table

echo "==> terraform init (dev backend)"
terraform init -backend-config=envs/backend-dev.hcl -input=false

echo "==> Plan (what will be destroyed):"
terraform plan -destroy -var-file=envs/dev.tfvars

echo
echo "!! This DESTROYS all DigiShield DEV infrastructure."
echo "!! The dev database has NO final snapshot — data will be lost."
read -r -p 'Type "destroy dev" to continue: ' reply
[ "$reply" = "destroy dev" ] || {
  echo "Aborted."
  exit 1
}

# Empty stateful stores that have no force_destroy, so destroy won't fail.
bucket="$(terraform output -raw frontend_bucket 2>/dev/null || true)"
if [ -n "$bucket" ]; then
  echo "==> Emptying S3 bucket $bucket"
  aws s3 rm "s3://$bucket" --recursive || true
fi

echo "==> Destroying dev infrastructure..."
terraform destroy -var-file=envs/dev.tfvars -auto-approve

echo
echo "==> Done. Dev infra removed; cost ~\$0."
echo "    Kept (bootstrap, outside Terraform): state bucket + DynamoDB lock table."
echo "    Recreate with: terraform apply -var-file=envs/dev.tfvars"
