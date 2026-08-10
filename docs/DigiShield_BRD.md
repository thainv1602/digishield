# DigiShield — Business Requirements Document (BRD)

> Version 1.0 · 10/08/2026
> Companion documents: `DigiShield_FRD.md` (functional requirements),
> `DigiShield_Technical_Design.md` (how it is built), `DigiShield_ADR.md` (why),
> `DigiShield_Architecture.vi.html` (what actually runs).

This document states the **business problem, who it is for, and what success means**.
It deliberately contains no technology choices — those belong in the ADR and the TDD.

---

## 1. Problem statement

Phishing succeeds because it targets people, not systems. An organisation can buy
mail filtering and endpoint protection and still lose credentials the first time a
convincing message reaches an untrained employee. The gap is not detection; it is
that nobody knows **which of their people are vulnerable, to what, and whether
training changed anything**.

Vietnamese organisations face this with two additional constraints:

- **Content in Vietnamese.** Imported awareness platforms train staff against English
  lures that do not resemble the messages they actually receive — bank impersonation,
  Zalo scams, fake delivery notices, callback fraud.
- **Data residency.** Government agencies cannot send employee data to a foreign SaaS,
  and in many cases cannot reach the public internet at all.

## 2. Business objectives

| # | Objective | Success measure |
|---|---|---|
| BO-1 | Make human risk **measurable** | Every employee carries a risk score derived from observed behaviour, not from a questionnaire |
| BO-2 | Turn a mistake into **training at the moment it happens** | A user who clicks a simulated lure is enrolled in remediation automatically, without an administrator acting |
| BO-3 | Prove that training **works** | Repeat-simulation click rate falls measurably between campaigns, per department |
| BO-4 | Give the security team a **queue, not an inbox** | Reported phishing is triaged, ranked and converted into blocklist entries or training material |
| BO-5 | Serve regulated customers | The whole platform runs on-premise, air-gapped, with no external dependency required for core function |

## 3. Stakeholders

| Stakeholder | Interest | Primary outcome they judge us on |
|---|---|---|
| **Learner** (employee, student) | Not being embarrassed; short, relevant training | Time spent, and whether the lesson felt worth it |
| **Manager** | Their department's exposure | Department risk trend, completion rate |
| **Org Admin** | Programme ownership, user lifecycle | Coverage of the workforce; policy compliance |
| **Content Editor** | Realistic, safe simulation material | Templates approved and in use |
| **Analyst / SOC** | Real threats surfacing quickly | Time from user report to blocklist or alert |
| **Super Admin** (platform operator) | Multiple tenants, plans, quotas | Tenant health; usage within plan |
| **Buyer** (CISO, IT director) | Evidence for the board | A report that shows risk falling over time |

## 4. Scope

### 4.1 In scope

- **Simulation** — multi-channel phishing campaigns (email, SMS, QR, voice, attachment,
  USB, OTT) with delivery, open/click/submit tracking and per-campaign funnels
- **Learning** — course catalogue, enrolment, assessments, certificates, gamification
- **Reporting** — user-submitted phishing reports, analyst triage, blocklist, threat intel
- **Analytics** — behavioural risk scoring per user and department, benchmarking, dashboards
- **AI** — generation of simulation and coaching content, classification and moderation,
  orchestration of campaign/training decisions
- **Interception** — real-time warning on suspicious transactions and accounts
- **Tenancy** — organisations, groups, plans, quotas, usage metering, feature flags
- **Auth** — SSO/OIDC sign-in, six-role RBAC, provisioning, audit

### 4.2 Out of scope

| Not in scope | Why |
|---|---|
| Mail filtering / gateway | The platform trains people; it does not sit in the mail path |
| Endpoint or network security | Different product category |
| Real credential capture | Simulated forms record **that** a credential was entered, never the value |
| Executable payloads | Simulated attachments and USB drops are inert by policy — see FRD |
| Payment processing | Plans and quotas are modelled; billing settlement is external |

## 5. Constraints

| # | Constraint | Consequence |
|---|---|---|
| BC-1 | Government tenants require **on-premise / air-gapped** deployment | No mandatory dependency on any managed cloud service; every external integration must degrade |
| BC-2 | Employee data must remain in-country | Rules out foreign-hosted SaaS components for regulated tenants |
| BC-3 | One database serves many organisations | Tenant isolation is a correctness requirement, not a feature |
| BC-4 | Content must be Vietnamese-first | Simulation and training material authored in Vietnamese; interface bilingual |
| BC-5 | Simulation touches real employees | Any content that reaches a person must be reviewable and revocable |

## 6. Assumptions

- Organisations can supply an employee directory (SSO/SCIM) or accept CSV import.
- Simulated messages are permitted by the customer's own acceptable-use policy —
  the platform is used **with** the organisation's consent, never against it.
- Learners have a browser; no client software is required.

## 7. Business processes

Three processes are specified as BPMN and are the backbone of the product:

| Process | File | Summary |
|---|---|---|
| Simulation campaign | `DigiShield_bpmn_sim_campaign.bpmn` | Plan → approve content → deliver → track → coach |
| Incident response | `DigiShield_bpmn_incident_response.bpmn` | Report → triage → confirm → blocklist / broadcast |
| Content approval | `DigiShield_bpmn_content_approval.bpmn` | Draft → review → approve or reject → publish |

## 8. Business rules

| # | Rule |
|---|---|
| BR-1 | A simulation may only target employees of the tenant that owns the campaign |
| BR-2 | Simulated credential forms record only *whether* input occurred — never the value |
| BR-3 | Simulated attachments and USB payloads must be inert: no macros, nothing executable |
| BR-4 | Content that reaches an employee must have been approved by someone other than its author |
| BR-5 | A user who fails a simulation is enrolled in remediation without administrator action |
| BR-6 | Every sensitive action leaves an audit record naming actor, target and time |
| BR-7 | One organisation may never read another organisation's data, under any role |

## 9. Risks

| # | Risk | Mitigation |
|---|---|---|
| BRK-1 | Employees perceive simulations as entrapment | Coaching-first framing; no individual naming in reports to peers; BR-5 makes the outcome training, not punishment |
| BRK-2 | Simulation content is mistaken for a real attack and triggers an incident | Campaign registry visible to the SOC; reported simulations recognised and closed automatically |
| BRK-3 | Generated content is offensive or too realistic | BR-4 approval gate plus AI moderation |
| BRK-4 | Tenant data leak | Two independent isolation layers (application guard and database row-level security) |
| BRK-5 | Dependence on an external AI provider | The AI client degrades to a deterministic local implementation; a self-hosted model is a supported path |

## 10. Glossary

| Term | Meaning |
|---|---|
| **Tenant** | One customer organisation; the unit of data isolation |
| **Campaign** | One simulation run against a chosen audience over a period |
| **Lure** | The simulated message, page or file a campaign delivers |
| **Funnel** | Delivered → opened → clicked → submitted, per campaign |
| **Remediation** | Training assigned as a consequence of failing a simulation |
| **Risk score** | 0–100 per user, derived from observed behaviour |
| **Triage** | Analyst decision on a reported message: confirm as threat or dismiss |
| **Coaching page** | The short lesson shown at the moment of a click |
