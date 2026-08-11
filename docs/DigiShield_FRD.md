# DigiShield — Functional Requirements Document (FRD)

> Version 1.0 · 10/08/2026
> Derived from `DigiShield_BRD.md`. Every requirement below traces to a real endpoint
> in `DigiShield_openapi.yaml` (62 paths / 81 operations) and to the module that owns it.

**How to read a requirement.** Each row carries an ID, the actor who exercises it, the
API surface it is observable through, and the owning module. A requirement is *done*
when the endpoint exists, the behaviour is covered by the five mandatory test layers,
and the owning module meets the coverage floor in §8.

**Role abbreviations** — `SA` Super Admin · `OA` Org Admin · `MG` Manager ·
`CE` Content Editor · `AN` Analyst · `LN` Learner. Roles are hierarchical: a higher role
satisfies a lower one. Authoritative mapping: `AUTHZ_MATRIX.md`.

---

## 1. Auth & identity — `modules/auth`

| ID | Requirement | Actor | API | Notes |
|---|---|---|---|---|
| FR-AUTH-01 | Sign in and receive an access + refresh token pair | all | `POST /auth/login` | Cognito OIDC in deployed builds; dev form otherwise |
| FR-AUTH-02 | Renew an access token without re-authenticating | all | `POST /auth/refresh` | Renews the credential only, not the identity |
| FR-AUTH-03 | Complete the hosted-UI redirect and establish a session | all | — (client-side OIDC callback) | Authorization code must be removed from the address bar |
| FR-AUTH-04 | Request and complete a password reset | all | `POST /auth/forgot-password`, `/auth/reset-password` | |
| FR-AUTH-05 | Enrol, verify and satisfy a second factor | all | `/auth/mfa/setup`, `/verify`, `/challenge` | |
| FR-AUTH-06 | Resolve the signed-in principal, including tenant and role | all | `GET /auth/me` | Source of the tenant used to scope every later call |
| FR-AUTH-07 | List, create, update and deactivate users of the tenant | OA | `/users`, `/users/{id}` | Deactivation must preserve historical records |
| FR-AUTH-08 | Import users in bulk | OA | `POST /users/import` | Reports accepted and rejected rows separately |
| FR-AUTH-09 | Suspend a user whose mandatory training is overdue | system | `/users/{id}/suspension` | Triggered by `EnrollmentDueEvent`; never applied to non-learners |

## 2. Tenancy, plans & quotas — `modules/tenancy`

| ID | Requirement | Actor | API | Notes |
|---|---|---|---|---|
| FR-TEN-01 | Create an organisation together with its first administrator | SA | `POST /tenants` | Atomic: no tenant without an owner |
| FR-TEN-02 | Read and update organisation profile and settings | SA, OA (own) | `/tenants/{id}`, `/settings` | Org admin restricted to own tenant |
| FR-TEN-03 | Manage groups and their membership | OA | `/groups` | Groups are the audience unit for campaigns |
| FR-TEN-04 | Offer subscription plans with defined limits | SA | `/plans`, `/subscription` | |
| FR-TEN-05 | Meter usage per period | system | `GET /tenants/{id}/usage` | Users, campaigns, messages delivered |
| FR-TEN-06 | Refuse actions that would exceed the plan quota | system | all mutating endpoints | Must fail with a meaningful error, not a server error |
| FR-TEN-07 | Enable or disable features per plan tier | SA | `/feature-flags` | |
| FR-TEN-08 | Record every sensitive action in an audit log | system | `GET /audit` | Actor, target, time, severity — BR-6 |

## 3. Simulation — `modules/simulation`

| ID | Requirement | Actor | API | Notes |
|---|---|---|---|---|
| FR-SIM-01 | Create a campaign targeting a group, on a channel, in a window | OA, CE | `POST /sim/campaigns` | Channels: email, SMS, QR, voice, attachment, USB, OTT |
| FR-SIM-02 | Deliver campaign messages to the selected audience | system | — (event driven) | Emits `SimulationDeliveryRequestedEvent` |
| FR-SIM-03 | Record delivery, open, click and submit per recipient | system | `POST /sim/events`, `/sim/track/{token}` | Duplicate delivery must not double-count |
| FR-SIM-04 | Publish a click so other modules can react | system | — | `UserClickedSimulationEvent` |
| FR-SIM-05 | Show the funnel and per-recipient outcome of a campaign | OA, MG | `GET /sim/campaigns/{id}` | Delivered → opened → clicked → submitted |
| FR-SIM-06 | Record that a credential form was submitted, never its content | system | — | **BR-2. Verified by a test asserting no credential field is persisted** |
| FR-SIM-07 | Recognise a reported simulation and close it without SOC effort | system | — | Prevents BRK-2 |

## 4. Learning — `modules/learning`

| ID | Requirement | Actor | API | Notes |
|---|---|---|---|---|
| FR-LRN-01 | Publish a course catalogue with levels and languages | CE | `/courses` | |
| FR-LRN-02 | Enrol a learner, manually or automatically | OA, system | `/enrollments` | |
| FR-LRN-03 | Enrol a user in remediation on simulation failure | system | — | **BR-5.** Exactly once per click; must not double-enrol |
| FR-LRN-04 | Deliver a coaching page at the moment of a click | LN | `/coaching-pages` | |
| FR-LRN-05 | Run assessments and record responses and results | LN | `/assessments`, `/responses`, `/results` | |
| FR-LRN-06 | Place a learner by prior competence | LN | `POST /assessments/placement` | Feeds the adaptive path |
| FR-LRN-07 | Issue and verify a certificate | LN | `/certificates`, `/certificates/{id}` | Verification link must work without sign-in |
| FR-LRN-08 | Award points and badges, and rank learners | system | `/points`, `/badges`, `/gamification/leaderboard` | |
| FR-LRN-09 | Track compliance against a policy | OA, MG | `/compliance/policies`, `/compliance/status` | |

## 5. Reporting & threat intel — `modules/reporting`

| ID | Requirement | Actor | API | Notes |
|---|---|---|---|---|
| FR-REP-01 | Let a user report a suspicious message | LN | `POST /reports/phishing` | Entry point for the incident-response BPMN |
| FR-REP-02 | Let an analyst confirm or dismiss a report | AN | `PATCH /reports/phishing/{id}` | **Both outcomes must be audited** |
| FR-REP-03 | Publish a confirmation so risk and training can react | system | — | `PhishingReportConfirmedEvent` |
| FR-REP-04 | Maintain a blocklist of confirmed indicators | AN | `/blacklist` | |
| FR-REP-05 | Broadcast an alert to the organisation | AN | `POST /alerts/broadcast` | |
| FR-REP-06 | Convert confirmed threat intel into training material | AN | `POST /threat-intel/{id}/convert` | Emits `ThreatIntelConvertedEvent` |
| FR-REP-07 | Export report data | OA, AN | *not implemented* | **Exported cells must not be interpretable as spreadsheet formulas.** No backend export endpoint exists yet — scheduled in topic ĐT13 |

## 6. Analytics — `modules/analytics`

| ID | Requirement | Actor | API | Notes |
|---|---|---|---|---|
| FR-ANA-01 | Maintain a 0–100 risk score per user from observed behaviour | system | `GET /analytics/risk` | **BO-1.** Deterministic for a given signal set |
| FR-ANA-02 | Aggregate risk by department and organisation | MG, OA | `GET /analytics/risk` | |
| FR-ANA-03 | Recompute on each relevant behavioural event | system | — | Click, report, course completion |
| FR-ANA-04 | Publish a recomputation so other modules can segment | system | — | `RiskRecomputedEvent` |
| FR-ANA-05 | Compare an organisation against a benchmark | OA | `GET /analytics/benchmark` | **BO-3** |
| FR-ANA-06 | Provide dashboard metrics for each role | all | `GET /analytics/dashboard` | |

## 7. Notification, AI, interception

| ID | Requirement | Actor | API | Module |
|---|---|---|---|---|
| FR-NOT-01 | Deliver notifications on at least email and in-app | system | `/notifications` | notification |
| FR-NOT-02 | Send enrolment and due-date reminders | system | `/notifications/reminders` | notification |
| FR-NOT-03 | Push an alert to connected clients in real time | system | WebSocket | notification |
| FR-AI-01 | Generate simulation and coaching content | CE | `POST /ai/templates/generate` | ai |
| FR-AI-02 | Classify a reported message | system | `POST /ai/classify` | ai |
| FR-AI-03 | Moderate generated content before it can be approved | system | `POST /ai/moderate` | ai |
| FR-AI-04 | Orchestrate campaign and training decisions from risk | system | `POST /ai/orchestration/run` | ai |
| FR-AI-05 | Function with no external AI provider reachable | system | — | Degrades to a deterministic local client |
| FR-AI-06 | Require approval by someone other than the author before content reaches an employee | CE, OA | `/ai/templates/{id}` | **BR-4 — not yet implemented; see topic ĐT5** |
| FR-INT-01 | Evaluate a transaction or account for fraud indicators | system | `POST /interventions/evaluate` | interception |
| FR-INT-02 | Maintain an account watchlist and check against it | AN | `/account-watchlist`, `/check` | interception |

## 8. Non-functional requirements

| ID | Requirement | Verification |
|---|---|---|
| NFR-SEC-01 | One organisation cannot read another's data under any role | Integration test on real PostgreSQL with row-level security enabled |
| NFR-SEC-02 | Every endpoint enforces the minimum role in `AUTHZ_MATRIX.md` | Test per endpoint × role — allowed and denied |
| NFR-SEC-03 | No credential value from a simulated form is ever persisted | Explicit negative test — FR-SIM-06 |
| NFR-SEC-04 | Exports cannot carry executable spreadsheet content | Test on the export escaper |
| NFR-OPS-01 | Core function requires no external managed service | Full suite passes with no outbound network |
| NFR-I18N-01 | Interface and generated content available in Vietnamese and English | Locale of the **recipient** decides, not the author |
| NFR-PERF-01 | Tracking endpoints absorb campaign-scale bursts | Load test on the beacon path |

### 8.1 Test coverage policy

Five test layers are mandatory for every requirement: backend unit, backend integration,
frontend unit, API collection (Newman), end-to-end. A requirement with a passing
implementation but a missing layer is **not** complete.

**Per endpoint**, three artefacts are required without exception:

| Artefact | Obligation |
|---|---|
| OpenAPI | Declared in `DigiShield_openapi.yaml`. Absent means the generated frontend client cannot reach it, however well the backend works |
| Postman | A request with a `pm.test` script asserting status, schema, required fields, **and at least one failure case**. A request without assertions does not count |
| Selenium | At least one scenario for any endpoint serving a user-facing flow. Internal service-to-service endpoints are exempt, stated as such in the pull request |

**Sequence diagrams are tiered, not universal.** Required when a flow crosses two or more
modules, publishes or consumes an event, calls an external service, or makes an
authorization decision beyond ordinary `@PreAuthorize`. Not required for CRUD confined to
one module: a repeated controller → service → repository chain carries no information, and
eighty of them would bury the fifteen that do.

**Coverage floor — line coverage, measured per module.**

| Stage | Backend floor | Notes |
|---|---|---|
| Today | 0.50 | A ratchet set just under the weakest module |
| Target | **0.90** | Per subproject, not aggregate — an average cannot hide an untested module |

The floor rises in steps; each step is only taken once every module clears it, so the
gate never sits above reality. The frontend carries its own schedule and a lower interim
target, because it starts far below the backend.

Excluded from measurement, and only these: generated sources (the OpenAPI client),
framework configuration classes, and `package-info`. Nothing is excluded because it is
merely inconvenient to test.

> **Distributing the work.** Reaching 0.90 is not one team's task. Each of the nineteen
> capstone topics (`digishield/STUDENT_TOPICS.html`) owns one or more modules; the floor
> is an acceptance criterion of the topic that touches the module. Maintaining the gate
> itself belonged to a quality topic that has since been dropped, so nobody currently
> stops the floor slipping back.
>
> The rungs, the per-module cost of each, and the procedure for raising the floor are in
> **[`DigiShield_Coverage_Roadmap.md`](DigiShield_Coverage_Roadmap.md)**.

## 9. Traceability

| Direction | Where |
|---|---|
| Business objective → requirement | BO-1 → FR-ANA-01 · BO-2 → FR-LRN-03 · BO-3 → FR-ANA-05 · BO-4 → FR-REP-01…06 · BO-5 → NFR-OPS-01 |
| Business rule → requirement | BR-2 → FR-SIM-06 · BR-3 → topic ĐT3 · BR-4 → FR-AI-06 · BR-5 → FR-LRN-03 · BR-6 → FR-TEN-08, FR-REP-02 · BR-7 → NFR-SEC-01 |
| Requirement → API | Column *API* above; authoritative definition in `DigiShield_openapi.yaml` |
| Requirement → module | Section heading; module boundaries enforced by `ModularityTests` |
| Requirement → test | Five layers per §8.1; per-module coverage reported by CI |

## 10. Known gaps

Stated here rather than left implicit, because an FRD that only lists what exists is a
brochure.

| Gap | Requirement | Where it is scheduled |
|---|---|---|
| Content approval is a boolean set by the author | FR-AI-06 | Topic ĐT5 |
| `RiskRecomputedEvent` is published but nothing consumes it | FR-ANA-04 | Topic ĐT12 |
| Notification gateways are stubs; realtime push is a no-op | FR-NOT-01…03 | Topic ĐT16 |
| Quota enforcement is not applied at mutating endpoints | FR-TEN-06 | Topic ĐT15 |
| Backend messages are hardcoded Vietnamese | NFR-I18N-01 | Topic ĐT18 |
| The authorization matrix has no test coverage | NFR-SEC-02 | Topic ĐT19 |
| Coverage is 40% backend, well under the 0.90 target | §8.1 | Distributed across all topics |
| No backend export endpoint exists | FR-REP-07 | Topic ĐT13 |
