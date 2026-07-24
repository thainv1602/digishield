variable "name" {
  description = "Name prefix for the user pool and related resources (e.g. digishield-jetson)."
  type        = string
}

variable "dev_tenant_id" {
  description = "Fixed tenant UUID injected as the JWT `tid` claim by the pre-token-generation Lambda. Must match a tenant_id present in the DB (RLS filters on it). Defaults to the seeded demo tenant."
  type        = string
  default     = "11111111-1111-1111-1111-111111111111"
}

variable "callback_urls" {
  description = "Exact OAuth callback URLs (no wildcards; non-localhost must be HTTPS)."
  type        = list(string)
}

variable "logout_urls" {
  description = "Exact OAuth logout URLs."
  type        = list(string)
}
