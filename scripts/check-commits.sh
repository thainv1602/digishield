#!/usr/bin/env bash
#
# Validate every commit subject in a range (used by the commit-lint CI job).
#
# Usage: check-commits.sh <base-ref> <head-ref>
#   e.g. check-commits.sh origin/main HEAD

set -euo pipefail

base="${1:?base ref required}"
head="${2:-HEAD}"
repo_root="$(git rev-parse --show-toplevel)"

range="${base}..${head}"
commits="$(git rev-list "$range")"

if [ -z "$commits" ]; then
  echo "No commits to validate in $range."
  exit 0
fi

status=0
while read -r sha; do
  [ -z "$sha" ] && continue
  subject="$(git log -1 --format=%s "$sha")"
  if bash "$repo_root/scripts/validate-commit-subject.sh" "$subject"; then
    echo "✓ ${sha:0:8}  $subject"
  else
    echo "  (commit ${sha:0:8})" >&2
    status=1
  fi
done <<< "$commits"

if [ "$status" -ne 0 ]; then
  echo "" >&2
  echo "One or more commit messages do not meet the standard. Fix them with an" >&2
  echo "interactive rebase (git rebase -i $base) and force-push the branch." >&2
fi

exit "$status"
