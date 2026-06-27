#!/usr/bin/env bash
#
# One-time per-clone setup: point git at the tracked hooks in .githooks/.
# Run from anywhere inside the repo:  bash scripts/setup-hooks.sh

set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
git -C "$repo_root" config core.hooksPath .githooks
chmod +x "$repo_root"/.githooks/* "$repo_root"/scripts/*.sh 2>/dev/null || true

echo "✓ Git hooks enabled (core.hooksPath=.githooks)."
echo "  - commit-msg: Conventional Commits + English subject"
echo "  - pre-push:   blocks direct pushes to main/master/develop"
