#!/usr/bin/env bash
#
# Validate a single commit SUBJECT line against the project conventions.
# Used by both the local commit-msg hook and the commit-lint CI job, so the
# rules stay in exactly one place.
#
# Usage: validate-commit-subject.sh "<subject line>"
# Exit:  0 = valid, 1 = invalid (reason printed to stderr).

set -euo pipefail

subject="${1-}"

# Allow git-generated / housekeeping subjects we don't author by hand.
case "$subject" in
  Merge\ *|Revert\ \"*|"fixup! "*|"squash! "*|"amend! "*)
    exit 0
    ;;
esac

types='build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test'
pattern="^(${types})(\([a-z0-9._/-]+\))?(!)?: .+"

fail() {
  echo "✗ Invalid commit subject: '$subject'" >&2
  echo "" >&2
  echo "  $1" >&2
  echo "" >&2
  echo "  Required format (Conventional Commits):" >&2
  echo "    <type>(<optional-scope>): <subject in English>" >&2
  echo "    type ∈ {build, chore, ci, docs, feat, fix, perf, refactor, revert, style, test}" >&2
  echo "    e.g.  feat(auth): add JWT refresh endpoint" >&2
  echo "          fix(frontend): correct tsc project references" >&2
  exit 1
}

# 1. Conventional Commits structure.
if ! printf '%s' "$subject" | grep -Eq "$pattern"; then
  fail "It must start with a valid type and a colon (see format below)."
fi

# 2. English only — reject any non-ASCII byte (catches Vietnamese diacritics).
if printf '%s' "$subject" | LC_ALL=C grep -q '[^ -~]'; then
  fail "The subject must be written in English (ASCII characters only)."
fi

# 3. Length sanity.
len=${#subject}
if [ "$len" -gt 100 ]; then
  fail "The subject is too long (${len} > 100 characters)."
fi

exit 0
