variable "name" {
  description = "Name prefix for the user pool and related resources (e.g. digishield-jetson)."
  type        = string
}

variable "callback_urls" {
  description = "Exact OAuth callback URLs (no wildcards; non-localhost must be HTTPS)."
  type        = list(string)
}

variable "logout_urls" {
  description = "Exact OAuth logout URLs."
  type        = list(string)
}
