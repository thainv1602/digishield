#!/usr/bin/env bash
#
# Apply branch protection to main (and develop) via the GitHub API.
# Requires the GitHub CLI authenticated as a repo ADMIN:  gh auth login
#
# Enforces:
#   - no direct pushes (changes must come via PR)
#   - PR cannot merge unless required CI checks pass
#   - PR needs approving review(s), including a Code Owner (admin / team lead)
#   - rules also apply to admins (enforce_admins)
#
# Usage: bash scripts/setup-branch-protection.sh [owner/repo]
#   defaults to the 'origin' remote's repo.

set -euo pipefail

REPO="${1:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
echo "Applying branch protection to: $REPO"

# Required status check contexts = the *job names* of the CI workflows.
# Adjust if you rename jobs. (Job 'name:' values, not workflow names.)
REQUIRED_CHECKS='[
  "Build & Test",
  "Build & push Docker image",
  "Lint, Typecheck, Test & Build",
  "Validate commit messages"
]'

protect() {
  local branch="$1"
  echo "  -> $branch"
  gh api -X PUT "repos/$REPO/branches/$branch/protection" \
    --input - <<JSON
{
  "required_status_checks": {
    "strict": true,
    "contexts": $REQUIRED_CHECKS
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "require_code_owner_reviews": true,
    "dismiss_stale_reviews": true
  },
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true
}
JSON
}

protect main

# Protect develop only if it exists.
if gh api "repos/$REPO/branches/develop" >/dev/null 2>&1; then
  protect develop
else
  echo "  (skipping develop — branch does not exist yet; re-run after creating it)"
fi

echo "✓ Branch protection applied."
