# Remote state for the dev environment. Create the S3 bucket + DynamoDB lock
# table once (manually or a bootstrap config), then:
#   terraform init -backend-config=envs/backend-dev.hcl
bucket         = "digishield-tfstate-743337585084"
key            = "digishield/dev/terraform.tfstate"
region         = "ap-southeast-1"
dynamodb_table = "digishield-tflock"
encrypt        = true
