# DigiShield — Unfinished Functionality Checklist

Status snapshot of features that are stubbed, mocked, or wired to demo data.
The system runs end-to-end as a **demo/skeleton**; the items below are what stands
between that and a production-ready build. Generated from a codebase audit;
keep it updated as items land.

Legend: 🔴 core gap · 🟡 integration/mock · 🟢 cleanup · ✅ done

---

## 🌐 i18n (Vietnamese ⇄ English)

Dependency-free i18n (`src/shared/i18n/`): `I18nProvider` + `useT()` + a VI→EN
dictionary keyed by the Vietnamese source string (VI = identity, EN = lookup with
fallback). `LanguageSwitcher` (VI/EN) in the top bar, persisted to localStorage.
Language also syncs from the signed-in user's profile `locale` claim via
`LocaleSync` (precedence: explicit switcher choice > profile locale > VI).
- [x] Infra + switcher + app shell (Sidebar nav/sections, Topbar, page titles)
- [x] Login + Admin Dashboard fully localized
- [x] SOC pages (Inbox, Alert Center, Threat Intel, Intervention Log, Watchlist)
- [x] Learning pages (Portal, Course Catalog, Lesson Player, Quiz, Quiz Results)
- [x] Auth flows (Login, Forgot Password, MFA, SSO, Onboarding)
- [x] Admin (Dashboard, AIDA, Gamification, Org Settings, Content Studio)
- [x] Campaigns (Wizard, Results), Users, Compliance, Certificates
- [x] Super admin (Tenant Console, SCIM Config, Audit Log)

All user-facing feature pages are localized. Future new strings just need a
`t('…')` wrap + an EN entry in `messages.ts` (untranslated strings fall back to
Vietnamese automatically).

---

## 🔴 High priority — core product not running for real

### AI module — Claude integration (gated)
Behaviour moved behind an `AiClient` SPI: `StubAiClient` (deterministic default,
no token cost) and `ClaudeAiClient` (`@Primary`, active when
`digishield.ai.claude.enabled=true` + `ANTHROPIC_API_KEY`).
- [x] `classify()` — Claude Haiku 4.5 with strict-JSON output (`{label,confidence,reason}`)
- [x] `moderate()` — Claude Haiku 4.5 (`{verdict,reasons[]}`)
- [x] `generateTemplate()` — Claude Sonnet 4.6 generates the subject + difficulty
- [~] `runOrchestration()` — persists an `AidaRun` (history) but the real risk
      recompute → auto-enroll pipeline is still a deterministic stub
- [ ] Go-live (ops): set `AI_CLAUDE_ENABLED=true` + provide `ANTHROPIC_API_KEY`
      (e.g. via Secrets Manager / GH secret). `generateTemplate` only stores the
      subject (schema has no body column).

### Analytics — risk score & adaptive loop
- [x] `computeScore()` — was a placeholder returning `0`; now signal-based (sim-click history)
- [x] Wire the simulation-click → risk-recompute trigger (new `SimulationClickRiskListener`)
- [x] `PhishingReportConfirmedListener` — added `userId` to `PhishingReportConfirmedEvent`
      (reporting side) and wired the listener: a confirmed report is now recorded as a
      vigilant (risk-lowering) signal for the reporter and recomputes their score.
- [x] Dashboard risk-trend is now data-driven — `dashboard()` builds the trend from
      persisted org-scope `RiskScore` history (chronological), and the dev seeder writes
      ~3 months of org-risk points. (Benchmark reference values are intentional constants;
      recent-reports list is still demo data — needs a cross-module reporting query. In
      prod nothing writes org-scope risk yet — a scheduled org-risk rollup is a follow-up.)

### Notification — saves to DB but never delivers
- [x] `send()` — added a `NotificationGateway` SPI; `send()` now delivers via the gateway
      and marks SENT/FAILED. Default `LoggingNotificationGateway` (dev: persist + log, no
      send); an AWS SES email gateway (boot app) takes over when
      `digishield.notifications.email.ses.enabled=true`. Recipient email resolved from the
      auth module via a `RecipientResolver` SPI. Added `NotificationStatus.FAILED`.
- [x] `broadcastAlert()` — fans an in-app ALERT out to every user in the tenant (one
      per recipient) via a new `UserDirectory` SPI (boot app → auth `listUsers`).
      `POST /alerts/broadcast` now takes `{message, severity}` and returns the reach;
      `AlertCenterPage` composer wired via `useBroadcastAlert()`. (Role/department
      sub-segments not exposed in the UI yet.)
- [ ] SES go-live (ops): verify the SES domain, exit the SES sandbox, grant the pod
      `ses:SendEmail` via IRSA, then set `NOTIFICATIONS_SES_ENABLED=true`. SMS/push not wired.
- [ ] FE `soc/AlertCenterPage.tsx` (L75) — compose form is UI-only; wire `useBroadcastAlert()`

### Auth — dev-mode placeholders (`modules/auth/.../AuthServiceImpl.java`)
Repo already wires **Cognito** (`feat/cognito-login`); confirm which path each env uses.
- [ ] Confirm Cognito is the real auth path for dev/prod; treat `AuthServiceImpl` as dev fallback only
- [ ] If kept: real credential validation (L172), SAML/OAuth verify (L183), password reset
      (L189/L196), MFA verify (L212/L222), real QR for MFA setup (L301)

---

## 🟡 Medium priority — frontend wired to mock / missing endpoints

- [x] `soc/ThreatIntelPage.tsx` — live `useThreatIntel()` + ThreatFlip via `useConvertThreatIntel()`
- [x] `soc/InterventionLogPage.tsx` — live `useInterventions()`; CSV export (client-side from loaded rows)
- [x] `soc/SocInboxPage.tsx` — bulk + drawer triage wired via `useTriageReport()` /
      `useConvertReportToTraining()`
- [x] `learning/LearnerPortalPage.tsx` — report CTA opens a Drawer that submits to
      `POST /reports/phishing` via `useReportPhishing()` (was a dead `/learn/report` link)
- [x] `certificates/CertificatePage.tsx` — Download = browser print-to-PDF; Share = copy
      the verification link (no backend PDF/share endpoint exists)
- [x] `_shared/mockData.ts` removed (0 importers) + purged 24 committed `.fuse_hidden*`
      editor-orphan files and added a `.gitignore` rule for them
- [x] `content` template library — added `GET /ai/templates` (backend) and wired the
      library via `useTemplates()` (was a static array)
- [x] `campaigns` wizard — templates load from `GET /ai/templates`, audience from
      `GET /groups`; the selected template id is now sent on create. Channels stay a
      fixed enum (UI choice, not data). Note: the create endpoint doesn't yet accept the
      audience group, and `Group` carries no member count.
- [x] `admin` AIDA run history — `runOrchestration` now persists an `AidaRun`;
      added `GET /ai/orchestration/runs` + `AidaPage` loads it and triggers real runs
- [x] `admin` org-settings thresholds — added GET/PATCH `/tenants/{id}/thresholds`
      (new `business_thresholds` table); sliders load + save the real values
- [x] gamification point rules — added `GET /gamification/point-rules` (new
      `point_rule` table); GamificationPage loads them (was a static array)
- [x] `shared/.../TenantFilter.java` — now reads the `tid` claim from the validated
      JWT (resource-server) via `SecurityContextHolder`; dropped the forgeable
      `X-Tenant-Id` header fallback in production (fails closed without a claim).
      Cognito must emit the `tid` claim for prod.

---

## 🟢 Cleanup

- [x] Delete the duplicate `frontend/src/features/notification` (singular) — the real
      one is `notifications` (plural)
- [x] Delete 5 empty feature scaffolds whose UI lives elsewhere:
      `analytics` (→ dashboard), `interception` (→ soc), `simulation` (→ campaigns),
      `tenancy` (→ admin/super), plus the singular `notification` above
