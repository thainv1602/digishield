# Remote state for the prod environment.
#   terraform init -reconfigure -backend-config=envs/backend-prod.hcl
bucket         = "digishield-tfstate-<ACCOUNT_ID>"
key            = "digishield/prod/terraform.tfstate"
region         = "ap-southeast-1"
dynamodb_table = "digishield-tflock"
encrypt        = true
