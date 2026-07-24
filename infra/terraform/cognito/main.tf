provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = "digishield"
      ManagedBy = "terraform"
      Component = "cognito"
    }
  }
}

module "cognito" {
  source        = "../modules/cognito"
  name          = var.name
  dev_tenant_id = var.dev_tenant_id

  # localhost for local dev + the Jetson public HTTPS URL. Cognito requires exact
  # origins (no wildcards); every non-localhost URL must be HTTPS.
  callback_urls = ["http://localhost:5173", var.app_url]
  logout_urls   = ["http://localhost:5173", var.app_url]
}
