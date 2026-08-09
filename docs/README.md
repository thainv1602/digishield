# DigiShield — Project Docs

A platform for preventing, training against, and alerting on cyber fraud — for Enterprises, Government Agencies, and Schools.

*Updated: 28/06/2026*

## Documents

| File | Contents |
|------|----------|
| **DigiShield_Technical_Design.md** | Technical design: architecture, ER, sequence, BPMN, DFD, and multi-tenant (SaaS) architecture. |
| **DigiShield_Architecture.html** | Six architecture diagrams: system context, module boundaries, event flow, module internals, tenant isolation, CI/CD. Open in a browser. |
| **DigiShield_ADR.md** | Architecture decisions (ADR-001…004) + realization appendix. |
| **DigiShield_openapi.yaml** | API spec (OpenAPI 3.0.3). Import into Swagger/Postman or generate a client. |
| **DigiShield_UIUX_Spec.md** | UI/UX design: design system, app shell, wireframes for 6 roles. |
| **DigiShield_bpmn_*.bpmn** | Business processes: incident response, content approval, simulation campaign. |

> Open `.bpmn` files with [demo.bpmn.io](https://demo.bpmn.io) or Camunda Modeler.
> View `.md` files in a Markdown reader (VS Code, Obsidian) to render tables & Mermaid diagrams.
> `DigiShield_Architecture.html` is a self-contained page — no build step, no network. Its
> diagrams are hand-authored inline SVG, so GitHub's Markdown renderer would strip them; open
> the file in a browser instead. It follows the viewer's light/dark theme.

## Code

Two sibling directories of `docs/` in this monorepo:

- **`digishield/`** — Backend: Java 25, Spring Boot 4.1, Spring Modulith (Gradle multi-module).
  Build: `./gradlew build`. Details: `digishield/README.md`.
- **`frontend/`** — Frontend: React 18 + TypeScript + Vite, API client generated from `DigiShield_openapi.yaml`.
  Run dev: `npm install && npm run dev`. Details: `frontend/README.md`.

CI/CD lives at `.github/workflows/` (monorepo root); the backend workflow pushes its image to GHCR on `main`.

## Notes

- `DigiShield_openapi.yaml` is the source of truth for the API — regenerate the frontend client (`npm run gen:api`) whenever it changes.

## Contributing

This is a monorepo: backend (`digishield/`), frontend (`frontend/`), docs (`docs/`).

**One-time setup** — enable the shared git hooks (Conventional-Commit + push checks):

```bash
bash scripts/setup-hooks.sh
```

### Branching & merging

- **Never push directly to `main`** — it is protected (rejected by the pre-push hook and by GitHub branch protection).
- Branch off `main`, then open a Pull Request:

  ```bash
  git switch -c feat/short-description
  git push -u origin feat/short-description
  ```

- A PR can only merge when **all** hold:
  - ✅ CI passes (`backend-ci`, `frontend-ci`, `commit-lint`)
  - ✅ At least one approving review from a Code Owner (see [`.github/CODEOWNERS`](../.github/CODEOWNERS))
  - ✅ All review conversations resolved

### Commit messages

Follow [Conventional Commits](https://www.conventionalcommits.org/), written **in English**:

```
<type>(<optional-scope>): <subject in English>
```

- **type** ∈ `build` · `chore` · `ci` · `docs` · `feat` · `fix` · `perf` · `refactor` · `revert` · `style` · `test`
- subject: imperative mood, English (ASCII), ≤ 100 chars.

```
feat(auth): add JWT refresh endpoint
fix(frontend): repair tsc project references
docs: update backend README for Java 25
```

Enforced by the local `commit-msg` hook and the `commit-lint` CI check (source of truth: `scripts/validate-commit-subject.sh`).

### Branch protection (repo admin, one-time)

Requires the GitHub CLI authenticated as an admin (`gh auth login`):

```bash
bash scripts/setup-branch-protection.sh
```

This requires PRs, passing CI, and Code-Owner approval before merging into `main`, and applies the rules to admins too.
