# Contributing to DigiShield

This is a monorepo: backend (`digishield/`), frontend (`frontend/`), docs (`docs/`).

## One-time setup

Enable the shared git hooks (Conventional-Commit + push checks):

```bash
bash scripts/setup-hooks.sh
```

## Branching & merging rules

- **Never commit or push directly to `main` or `develop`.** They are protected;
  direct pushes are rejected locally (pre-push hook) and on GitHub (branch protection).
- Create a feature branch off `develop` (or `main`):

  ```bash
  git switch -c feat/short-description
  git push -u origin feat/short-description
  ```

- Open a **Pull Request**. It can only be merged when **all** of these hold:
  - ✅ All CI checks pass (`backend-ci`, `frontend-ci`, `commit-lint`).
  - ✅ At least one approving review from a **Code Owner** (admin / team lead — see
    [`.github/CODEOWNERS`](.github/CODEOWNERS)).
  - ✅ All review conversations resolved.

## Commit message standard

Every commit must follow **[Conventional Commits](https://www.conventionalcommits.org/)**
and be written **in English**:

```
<type>(<optional-scope>): <subject in English>
```

- **type** ∈ `build` · `chore` · `ci` · `docs` · `feat` · `fix` · `perf` ·
  `refactor` · `revert` · `style` · `test`
- subject: imperative mood, English (ASCII), ≤ 100 chars.

Examples:

```
feat(auth): add JWT refresh endpoint
fix(frontend): repair tsc project references
docs: update backend README for Java 25
```

Enforced by the local `commit-msg` hook and by the `commit-lint` CI check (the
single source of truth is `scripts/validate-commit-subject.sh`).

## Enabling branch protection (repo admin, one-time)

Requires the GitHub CLI authenticated as an admin (`gh auth login`):

```bash
bash scripts/setup-branch-protection.sh
```

This requires PRs, passing CI, and Code-Owner approval before merging into
`main`/`develop`, and applies the rules to admins too.
