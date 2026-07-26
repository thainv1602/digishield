variable "aws_region" {
  # The Jetson pool lives in us-east-1. The default used to say ap-southeast-1,
  # which is where the rest of the stack was meant to go — so a plain
  # `terraform apply` here proposed building a second pool in a region the
  # production one does not live in, and quietly dropped the real one from state
  # ("AWS resource not found during refresh... Automatically removing"). The
  # default now matches what is deployed, and terraform.tfvars pins it besides.
  description = "AWS region for the Cognito user pool. Must match where the pool actually exists."
  type        = string
  default     = "us-east-1"
}

variable "name" {
  description = "Name prefix for the user pool and related resources."
  type        = string
  default     = "digishield-jetson"
}

variable "dev_tenant_id" {
  description = "Fixed tenant UUID injected as the JWT `tid` claim. Must match a tenant_id present in the DB (RLS filters on it). Defaults to the seeded demo tenant."
  type        = string
  default     = "11111111-1111-1111-1111-111111111111"
}

variable "app_urls" {
  description = "Public HTTPS origins of the frontend allowed as Cognito callback/logout URLs. Each must be a stable, exact HTTPS origin (no wildcards, no trailing slash) — the SPA sends window.location.origin as the redirect_uri. localhost:5173 is always added for local dev."
  type        = list(string)
  default = [
    "https://ubuntu.tail761165.ts.net", # Jetson Tailscale Funnel (public)
    "https://digishield.duckdns.org",   # legacy dev ingress
  ]
}
