# Guard-railed Terraform wrapper.
#
# The root module is shared across environments; the env is chosen at `init`
# (backend) + `apply` (var-file) time, not in code. This Makefile removes the
# two ways that goes wrong:
#   1. Mixing a backend with the wrong tfvars  -> both derive from one ENV var.
#   2. Forgetting `terraform init -reconfigure` -> a preflight asserts the
#      initialized backend key matches ENV before any plan/apply/destroy.
# Prod apply/destroy additionally require typing the env name to confirm.
#
# Usage:
#   make init    ENV=dev     # (re)point the backend at the <env> state
#   make plan    ENV=dev
#   make apply   ENV=dev
#   make destroy ENV=dev
#   make fmt                 # (no ENV needed)
#
# ENV must be one of: dev prod

TF          ?= terraform
ENVS        := dev prod
BACKEND      = envs/backend-$(ENV).hcl
TFVARS       = envs/$(ENV).tfvars
STATE_CACHE := .terraform/terraform.tfstate
STATE_KEY    = digishield/$(ENV)/terraform.tfstate

.PHONY: init plan apply destroy validate output fmt help _guard-env _guard-init

## init: reconfigure the backend for ENV (run this when switching env)
init: _guard-env
	$(TF) init -reconfigure -backend-config=$(BACKEND)

## plan: terraform plan for ENV
plan: _guard-init
	$(TF) plan -var-file=$(TFVARS)

## apply: terraform apply for ENV (prod requires typed confirmation)
apply: _guard-init
	@if [ "$(ENV)" = "prod" ]; then \
	  printf 'About to APPLY to PROD. Type "prod" to continue: '; read ans; \
	  [ "$$ans" = "prod" ] || { echo "aborted."; exit 1; }; \
	fi
	$(TF) apply -var-file=$(TFVARS)

## destroy: terraform destroy for ENV (prod requires typed confirmation)
destroy: _guard-init
	@if [ "$(ENV)" = "prod" ]; then \
	  printf 'About to DESTROY PROD. Type "prod" to continue: '; read ans; \
	  [ "$$ans" = "prod" ] || { echo "aborted."; exit 1; }; \
	fi
	$(TF) destroy -var-file=$(TFVARS)

## validate: terraform validate for ENV
validate: _guard-init
	$(TF) validate

## output: show terraform outputs for ENV
output: _guard-init
	$(TF) output

## fmt: format all .tf files (no ENV needed)
fmt:
	$(TF) fmt -recursive

## help: list targets
help:
	@grep -E '^## ' $(MAKEFILE_LIST) | sed 's/^## /  /'

# --- guards -----------------------------------------------------------------

# ENV is present, valid, and its backend+tfvars files exist.
_guard-env:
	@if [ -z "$(ENV)" ]; then \
	  echo "ERROR: ENV is required.  Usage: make <target> ENV=dev|prod"; exit 1; fi
	@echo "$(ENVS)" | tr ' ' '\n' | grep -qx "$(ENV)" || { \
	  echo "ERROR: ENV='$(ENV)' is invalid. Allowed: $(ENVS)"; exit 1; }
	@test -f "$(BACKEND)" || { echo "ERROR: missing $(BACKEND)"; exit 1; }
	@test -f "$(TFVARS)"  || { echo "ERROR: missing $(TFVARS)";  exit 1; }

# Backend is initialized AND points at THIS env's state.
# Catches "forgot `terraform init -reconfigure` after switching env".
_guard-init: _guard-env
	@test -f "$(STATE_CACHE)" || { \
	  echo "ERROR: backend not initialized. Run: make init ENV=$(ENV)"; exit 1; }
	@grep -q "$(STATE_KEY)" "$(STATE_CACHE)" || { \
	  echo "ERROR: backend is initialized for a DIFFERENT env than '$(ENV)'."; \
	  echo "       expected key: $(STATE_KEY)"; \
	  echo "       current:      $$(grep -o '\"key\": *\"[^\"]*\"' $(STATE_CACHE) | head -1)"; \
	  echo "       fix:          make init ENV=$(ENV)"; exit 1; }
