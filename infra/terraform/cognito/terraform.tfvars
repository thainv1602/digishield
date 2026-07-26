# Committed on purpose: none of this is secret, and leaving the region to a
# default is what let `terraform apply` target the wrong one. With this file the
# directory applies correctly with no flags.
aws_region = "us-east-1"

# Both are registered on the live client. Dropping one silently de-registers a
# callback URL, which is a login outage for anyone still using it — keep this
# list matching what is deployed.
app_urls = ["https://ubuntu.tail761165.ts.net", "https://digishield.duckdns.org"]

name          = "digishield-jetson"
dev_tenant_id = "11111111-1111-1111-1111-111111111111"
