# DigiShield — Unfinished Functionality Checklist

Status snapshot of features that are stubbed, mocked, or wired to demo data.
The system runs end-to-end as a **demo/skeleton**; the items below are what stands
between that and a production-ready build. Generated from a codebase audit;
keep it updated as items land.

Legend: 🔴 core gap · 🟡 integration/mock · 🟢 cleanup · ✅ done

---

## 🔴 High priority — core product not running for real

### AI module — fully stubbed (`modules/ai/.../AiServiceImpl.java`)
Deterministic, dependency-free stubs; every real model call is a TODO.
- [ ] `classify()` (L79) — replace keyword matching with a real classification model
- [ ] `generateTemplate()` (L58) — call an LLM provider instead of hardcoded templates
- [ ] `moderate()` (L102) — call a content-safety service instead of a word-list rule
- [ ] `runOrchestration()` (L125) — trigger the real AIDA risk pipeline (currently no-op)

> When implementing, use the latest Claude models (Opus 4.8 / Sonnet 4.6).

### Analytics — risk score & adaptive loop
- [x] `computeScore()` — was a placeholder returning `0`; now signal-based (sim-click history)
- [x] Wire the simulation-click → risk-recompute trigger (new `SimulationClickRiskListener`)
- [x] `PhishingReportConfirmedListener` — added `userId` to `PhishingReportConfirmedEvent`
      (reporting side) and wired the listener: a confirmed report is now recorded as a
      vigilant (risk-lowering) signal for the reporter and recomputes their score.
- [ ] Dashboard trend/benchmark points are partly hardcoded (`AnalyticsServiceImpl` L114-134)

### Notification — saves to DB but never delivers
- [x] `send()` — added a `NotificationGateway` SPI; `send()` now delivers via the gateway
      and marks SENT/FAILED. Default `LoggingNotificationGateway` (dev: persist + log, no
      send); an AWS SES email gateway (boot app) takes over when
      `digishield.notifications.email.ses.enabled=true`. Recipient email resolved from the
      auth module via a `RecipientResolver` SPI. Added `NotificationStatus.FAILED`.
- [ ] `broadcastAlert()` (L65) — support segment/criteria-based fan-out to many users
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
- [ ] `_shared/mockData.ts` — remove once the pages above use generated hooks
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
