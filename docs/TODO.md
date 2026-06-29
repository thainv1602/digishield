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
- [ ] `send()` (L46) — integrate a real gateway (email/SMS/push) before marking SENT
- [ ] `broadcastAlert()` (L65) — support segment/criteria-based fan-out to many users
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
- [ ] Static reference data needing GET endpoints: `campaigns` (channels/templates/groups),
      `content` (template library), `admin` (thresholds, point rules, run history)
- [ ] `shared/.../TenantFilter.java` (L55) — read the `tid` claim from the JWT
      (currently falls back to a header) when the resource-server is integrated

---

## 🟢 Cleanup

- [x] Delete the duplicate `frontend/src/features/notification` (singular) — the real
      one is `notifications` (plural)
- [x] Delete 5 empty feature scaffolds whose UI lives elsewhere:
      `analytics` (→ dashboard), `interception` (→ soc), `simulation` (→ campaigns),
      `tenancy` (→ admin/super), plus the singular `notification` above
