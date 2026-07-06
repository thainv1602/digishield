# Authorization matrix (`@PreAuthorize`)

Role-based access control enforced via method security. **Only active in non-`dev`
profiles** (`@EnableMethodSecurity` lives on the `!dev` `SecurityConfig`), so it is
inert in the permissive `dev`/prod-like demo and enforces only in prod. Requires the
JWT `cognito:groups → ROLE_*` mapping (PR #51) to resolve roles.

## Role hierarchy (`MethodSecurityConfig`)
`SUPER_ADMIN` → `ORG_ADMIN` → (`MANAGER`, `ANALYST`, `CONTENT_EDITOR`) → `LEARNER`.
A higher role satisfies any lower one, so endpoints name the **minimum** role.

## Hybrid tenancy
Platform operations are `SUPER_ADMIN`-only. Tenant self-service uses
`hasRole('SUPER_ADMIN') or (hasRole('ORG_ADMIN') and @tenantGuard.isSelf(#tenantId))`
— an org admin may act only on **their own** `{tenantId}` (checked against the JWT
`tid` via `TenantAccessGuard`); a super admin is unrestricted. Marked **SELF** below.

| Endpoint | Min role |
|---|---|
| **Tenancy** |
| `GET/POST /tenants`, `PATCH /tenants/{id}`, `PUT /tenants/{id}/subscription`, `GET /super/scim` | SUPER_ADMIN |
| `GET/PATCH /tenants/{id}/settings\|thresholds\|feature-flags`, `GET /tenants/{id}\|usage\|subscription` | SELF |
| `GET/POST /groups`, `POST /groups/{id}/evaluate`, `GET /audit` | ORG_ADMIN |
| `GET /plans` | authenticated |
| **Auth / Users** |
| `/users/**`, `/scim/v2/Users/**` | ORG_ADMIN |
| `GET /auth/me` | authenticated |
| `/auth/login\|refresh\|sso\|forgot\|reset\|mfa/**` | pre-auth (see note) |
| **AI** |
| `POST /ai/orchestration/run`, `GET /ai/orchestration/runs` | ORG_ADMIN |
| `POST /ai/classify\|moderate` | ANALYST |
| `POST /ai/templates/generate`, `GET /ai/templates` | CONTENT_EDITOR |
| **Analytics** | `GET /analytics/**` | ANALYST or MANAGER |
| **Reporting / SOC** |
| `GET /reports/phishing`, `POST .../triage`, `.../convert-to-training`, `/blacklist/**`, `/threat-intel/**`, `GET /interventions`, `/account-watchlist` (GET list/POST) | ANALYST |
| `POST /reports/phishing` (submit) | LEARNER |
| `POST /interventions/evaluate`, `GET /account-watchlist/check` | authenticated |
| **Learning** |
| `POST /compliance/policies` | ORG_ADMIN |
| `GET /assessments` (list), `GET /compliance/policies` | MANAGER |
| `POST /assessments`, `POST /coaching-pages` | MANAGER or CONTENT_EDITOR |
| `GET /assessments/{id}/results` | MANAGER or ANALYST |
| everything else (courses, enrollments, lessons, quizzes, certificates, gamification, compliance status) | LEARNER |
| **Notification** |
| `POST /notifications`, `POST /alerts/broadcast` | ORG_ADMIN |
| `POST /notifications/reminders` | MANAGER |
| `GET /notifications` | LEARNER |
| **Simulation** |
| `GET/POST /sim/campaigns**` | MANAGER |
| `POST /sim/events` | authenticated |

## Notes / assumptions (review these)
- **Pre-auth auth endpoints** (`/auth/login`, `/refresh`, `/sso/callback`, `/forgot-password`, `/reset-password`, `/mfa/**`) are **not** annotated — they are the dev-stub login path and are reachable only via the URL rules in `SecurityConfig`/Cognito. Wiring real login is separate (AuthServiceImpl work).
- **Ingestion endpoints** `POST /interventions/evaluate`, `GET /account-watchlist/check`, `POST /sim/events` are left at `authenticated()` rather than role-gated, since they may be driven by a real-time integration or click-tracking. Tighten once the caller identity is settled.
- **SCIM** is gated `ORG_ADMIN`; if provisioned by a machine principal, it may want a dedicated scope instead.
- This is a **baseline** — adjust individual rows to the real product role model as needed.
