# DIGITAL SHIELD (DigiShield) — Technical Design Document (TDD)

> Version 1.0 · 06/27/2026
> Document with diagrams (Mermaid) + API specification. To be used alongside **DigiShield_Product_Technical_Spec_v2.docx** (product specification), **DigiShield_openapi.yaml** (machine-readable API specification) and **DigiShield_Architecture.html** (as-built architecture diagrams).
>
> The ```mermaid``` blocks render directly on GitHub, GitLab, VS Code (Mermaid extension), Obsidian, etc. If you need images, use [mermaid.live](https://mermaid.live) and paste the code to export PNG/SVG.

---

## Table of Contents
1. [Architecture Overview](#1-architecture-overview)
2. [System Context Diagram (C4 – Context)](#2-system-context-diagram-c4--context)
3. [Component Diagram (Container/Component)](#3-component-diagram-containercomponent)
4. [Deployment Diagram](#4-deployment-diagram)
5. [Data Model (ER Diagram)](#5-data-model-er-diagram)
6. [Business Flow Diagrams](#6-business-flow-diagrams)
7. [State Diagrams (Entity Lifecycles)](#7-state-diagrams-entity-lifecycles)
8. [Flowchart – Risk Score Calculation](#8-flowchart--risk-score-calculation)
9. [REST API Specification](#9-rest-api-specification)
10. [Event Model (Event/Queue)](#10-event-model-eventqueue)
11. [Security & Non-functional Requirements](#11-security--non-functional-requirements)
12. [100% Specification Closure Supplement](#12-100-specification-closure-supplement)
13. [WebSocket Specification (Realtime)](#13-websocket-specification-realtime)
14. [Traceability Matrix](#14-traceability-matrix)
15. [v1.2 Supplement — Proactive Detection & OTT Channels](#15-v12-supplement--proactive-detection--ott-channels)
16. [Use Case Diagram (UML)](#16-use-case-diagram-uml)
17. [BPMN Process Diagrams](#17-bpmn-process-diagrams)
18. [Data Flow Diagram (DFD)](#18-data-flow-diagram-dfd)
19. [Multi-tenant SaaS Architecture](#19-multi-tenant-saas-architecture)

---

## 1. Architecture Overview

> **Design intent vs. as-built.** Sections 1–4 describe the architecture as
> *designed*. For the architecture as *implemented* — module boundaries, the
> event fan-out, the layering inside a module, tenant isolation and the delivery
> pipeline — see **[`DigiShield_Architecture.html`](DigiShield_Architecture.html)**.
> Its six diagrams were drawn from the code (class, event and endpoint names are
> read out of the repository), so where the two documents disagree, that file
> reflects what is running and this one records what was planned. Open it in a
> browser: its diagrams are inline SVG, which GitHub's Markdown renderer strips.

DigiShield is a **multi-tenant SaaS** platform built on a **gradually-decoupling, event-driven modular monolith** architecture — the core is a single deployment unit with nine clearly bounded modules (Auth, Tenancy/Billing, Learning, Simulation, Reporting, Analytics, Notification, AI, Interception — Interception arrived with v1.2 and is specified in [§15](#15-v12-supplement--proactive-detection--ott-channels)), progressively split into microservices as scaling/team needs arise (see **ADR-001** in `DigiShield_ADR.md`). Each organization (tenant) has its data logically isolated via `org_id`/`tenant_id`. Heavy tasks (bulk email/SMS sending, AI analysis) run asynchronously through queues using a separate worker pool. Packaged in containers so it can run both in the cloud and on-premise/air-gapped (government sector); managed serverless is not used as the core.

**Key design principles:**
- API-first: every capability is exposed through a versioned REST API (`/api/v1`).
- Domain-Driven service separation: Auth, Learning, Simulation, Reporting, Analytics, Notification, AI.
- Asynchronous for high volumes: Message Queue + Workers.
- Defense in depth: JWT + RBAC + audit log + encryption.
- Flexible deployment: domestic cloud **or** on-premise (government sector).

---

## 2. System Context Diagram (C4 – Context)

**Description:** The highest-level diagram, showing the context in which DigiShield (the central block) sits — *who uses it* and *which external systems it connects to*. On the left are 4 user groups (Learner, Org Admin/Manager, Analyst SOC, Content Editor) with different usage purposes. On the right are 5 external systems the platform depends on: SSO (authentication), Email/SMS Gateway (sending simulations & notifications), NCSC/chongluadao.vn (blacklist synchronization — bidirectional arrow), and LLM API (AI content generation/classification). Read this diagram to identify the *integration scope* and stakeholders before diving into details.

```mermaid
graph TB
    subgraph Users[Users]
        A1[Learner<br/>Employee/Student]
        A2[Org Admin / Manager]
        A3[Analyst SOC]
        A4[Content Editor]
    end

    DS([DigiShield<br/>Prevention & Training Platform])

    subgraph Ext[External Systems]
        E1[SSO IdP<br/>Entra ID / Google]
        E2[Email Gateway<br/>SMTP/API]
        E3[SMS Brandname Gateway]
        E4[NCSC / chongluadao.vn<br/>Blacklist & alerts]
        E5[LLM API<br/>AI content & classification]
    end

    A1 -->|Learn, take quizzes, report| DS
    A2 -->|Administer, view reports| DS
    A3 -->|Handle reports, broadcast alerts| DS
    A4 -->|Author content| DS

    DS -->|Authenticate| E1
    DS -->|Send simulation/notification emails| E2
    DS -->|Send simulation SMS| E3
    DS <-->|Sync blacklist| E4
    DS -->|Generate/classify content| E5
```

---

## 3. Component Diagram (Container/Component)

**Description:** "Opens up" the system into 4 layers. The **Client Layer** includes the Web App, Browser Extension, and Email Add-in (where the "Report phishing" button lives). Every request passes through the **API Gateway** (JWT authentication, rate-limiting, routing) to **7 microservices** separated by business domain. Heavy tasks (bulk email/SMS sending, AI jobs) are pushed to the **Async Layer** (Message Queue + Workers + Scheduler) so they don't block requests. The **Data Layer** comprises PostgreSQL (business data), Redis (cache/queue), and Object Storage (media, reports). Note: only Simulation, Notification, and AI Service emit jobs to the queue.

```mermaid
graph LR
    subgraph Client[Client Layer]
        W[Web App<br/>Next.js/React]
        BX[Browser Extension<br/>Manifest V3]
        AD[Email Add-in]
    end

    GW[API Gateway<br/>Auth JWT · Rate-limit · Routing]

    subgraph Svc[Microservices]
        AUTH[Auth Service]
        LRN[Learning Service]
        SIM[Simulation Service]
        REP[Reporting Service]
        ANA[Analytics Service]
        NOT[Notification Service]
        AIS[AI Service]
    end

    subgraph Async[Async Layer]
        MQ[(Message Queue<br/>RabbitMQ/Kafka)]
        WK[Workers<br/>Email/SMS/AI jobs]
        SCH[Scheduler<br/>Cron campaigns]
    end

    subgraph Data[Data Layer]
        PG[(PostgreSQL<br/>multi-tenant)]
        RD[(Redis<br/>cache/queue)]
        OS[(Object Storage<br/>media/report)]
    end

    W & BX & AD --> GW
    GW --> AUTH & LRN & SIM & REP & ANA & NOT & AIS
    SIM & NOT & AIS --> MQ
    MQ --> WK
    SCH --> MQ
    AUTH & LRN & SIM & REP & ANA --> PG
    AUTH & ANA --> RD
    LRN & REP --> OS
    WK --> NOT
```

---

## 4. Deployment Diagram

**Description:** How the system actually runs on infrastructure. Traffic comes in via the **CDN/WAF** (attack protection, static distribution) → **Load Balancer** (TLS termination) → **Ingress** of the **Kubernetes** cluster, where the pods (API Gateway, services, workers) scale with load. The **Stateful/Managed** tier (PostgreSQL primary+replica, Redis, message broker, object storage) is separated from the pods for data durability. The **Observability** tier collects logs, metrics, and tracing. The same topology can be deployed **on-premise** for government agencies that must keep data in-country.

```mermaid
graph TB
    subgraph Edge
        CDN[CDN / WAF]
        LB[Load Balancer<br/>TLS termination]
    end

    subgraph K8s[Kubernetes Cluster]
        ING[Ingress Controller]
        subgraph Pods
            P1[API Gateway pods]
            P2[Service pods x N]
            P3[Worker pods x N]
        end
    end

    subgraph Stateful[Stateful / Managed]
        DB[(PostgreSQL<br/>Primary + Replica)]
        REDIS[(Redis cluster)]
        BUS[(Message Broker)]
        BLOB[(Object Storage)]
    end

    subgraph Obs[Observability]
        LOG[Log aggregation]
        MON[Metrics/Tracing]
    end

    CDN --> LB --> ING --> P1 --> P2
    P2 --> DB & REDIS & BUS & BLOB
    P3 --> BUS & DB
    P2 & P3 --> LOG & MON
```

> **On-premise:** same topology but deployed within the government agency's internal infrastructure, with data stored in-country, independent of external cloud.

---

## 5. Data Model (ER Diagram)

**Description:** Entity-relationship diagram of the core tables. At the center is `ORGANIZATIONS` (tenant) — nearly every business table has an `org_id` to isolate data between organizations. The main clusters: *user management* (organizations, users, departments, groups), *learning* (courses → lessons → quizzes, enrollments), *simulation* (sim_templates → sim_campaigns → sim_events), *reporting & alerting* (reports, alerts, blacklist_entries), and *measurement/audit* (risk_scores, audit_logs). Notation: `||--o{` = one-to-many, `}o--o{` = many-to-many (e.g., users ↔ groups). Each block lists representative fields along with primary key (PK)/foreign key (FK).

```mermaid
erDiagram
    ORGANIZATIONS ||--o{ USERS : has
    ORGANIZATIONS ||--o{ DEPARTMENTS : has
    ORGANIZATIONS ||--o{ GROUPS : has
    ORGANIZATIONS ||--o{ SIM_CAMPAIGNS : owns
    ORGANIZATIONS ||--o{ REPORTS : owns
    ORGANIZATIONS ||--o{ ALERTS : owns
    ORGANIZATIONS ||--o{ BLACKLIST_ENTRIES : owns
    DEPARTMENTS ||--o{ USERS : contains
    DEPARTMENTS ||--o{ DEPARTMENTS : parent
    USERS }o--o{ GROUPS : member
    COURSES ||--o{ LESSONS : contains
    COURSES ||--o{ ENROLLMENTS : has
    USERS ||--o{ ENROLLMENTS : takes
    LESSONS ||--o{ QUIZZES : has
    SIM_CAMPAIGNS ||--o{ SIM_EVENTS : generates
    USERS ||--o{ SIM_EVENTS : triggers
    SIM_TEMPLATES ||--o{ SIM_CAMPAIGNS : uses
    USERS ||--o{ REPORTS : submits
    REPORTS }o--|| BLACKLIST_ENTRIES : matches
    USERS ||--o{ RISK_SCORES : scored
    USERS ||--o{ AUDIT_LOGS : actor

    ORGANIZATIONS {
        uuid id PK
        string name
        enum type "enterprise|gov|edu"
        string data_region
        timestamp created_at
    }
    USERS {
        uuid id PK
        uuid org_id FK
        uuid department_id FK
        string email
        enum role
        enum status
        int risk_score
        string locale
    }
    DEPARTMENTS {
        uuid id PK
        uuid org_id FK
        uuid parent_id FK
        string name
        uuid manager_id
    }
    GROUPS {
        uuid id PK
        uuid org_id FK
        string name
        json rule_json "smart group"
    }
    COURSES {
        uuid id PK
        string domain
        enum level "basic|beginner|intermediate|advanced"
        string lang
        int duration_min
    }
    LESSONS {
        uuid id PK
        uuid course_id FK
        string title
        text content_ref
        int order
    }
    QUIZZES {
        uuid id PK
        uuid lesson_id FK
        json questions
        int pass_score
    }
    ENROLLMENTS {
        uuid id PK
        uuid user_id FK
        uuid course_id FK
        enum status
        int score
        timestamp completed_at
    }
    SIM_TEMPLATES {
        uuid id PK
        enum channel "email|sms|qr|usb|voice"
        string subject
        text body_ref
        enum difficulty
    }
    SIM_CAMPAIGNS {
        uuid id PK
        uuid org_id FK
        uuid template_id FK
        json target_filter
        timestamp scheduled_at
        enum status
    }
    SIM_EVENTS {
        uuid id PK
        uuid campaign_id FK
        uuid user_id FK
        enum action "delivered|open|click|submit|report"
        timestamp ts
    }
    REPORTS {
        uuid id PK
        uuid org_id FK
        uuid user_id FK
        text payload
        enum ai_label "clean|spam|threat"
        float ai_confidence
        enum status
    }
    ALERTS {
        uuid id PK
        uuid org_id FK
        enum severity
        string source
        text message
        timestamp broadcast_at
    }
    BLACKLIST_ENTRIES {
        uuid id PK
        uuid org_id FK
        enum type "url|phone|account"
        string value
        string source
        timestamp added_at
    }
    RISK_SCORES {
        uuid id PK
        enum scope "user|dept|org"
        uuid scope_id
        int value
        timestamp computed_at
    }
    AUDIT_LOGS {
        uuid id PK
        uuid org_id FK
        uuid actor_id FK
        string action
        string target
        string ip
        timestamp ts
    }
```

---

## 6. Business Flow Diagrams

### 6.1. SSO Login (SAML/OAuth)

**Description:** Single sign-on (SSO) login flow for enterprise/government. The user is redirected by the Auth Service to the identity provider (IdP) for authentication; when the IdP returns a valid assertion, the system **automatically creates/syncs the account and assigns the role**, then issues a JWT pair (access + refresh). Points to note when coding: mapping IdP attributes → role and handling first-time login users (just-in-time provisioning).

```mermaid
sequenceDiagram
    actor U as User
    participant W as Web App
    participant GW as API Gateway
    participant AUTH as Auth Service
    participant IDP as SSO IdP

    U->>W: Access DigiShield
    W->>GW: GET /auth/sso/login?org=...
    GW->>AUTH: Initiate SSO
    AUTH-->>W: Redirect to IdP
    W->>IDP: Log in (SAML/OAuth)
    IDP-->>W: Callback + token/assertion
    W->>GW: POST /auth/sso/callback
    GW->>AUTH: Validate assertion
    AUTH->>AUTH: Create/sync user, assign role
    AUTH-->>W: JWT (access + refresh)
    W-->>U: Enter dashboard by role
```

### 6.2. Phishing Simulation Campaign + Click Tracking + Just-in-time Coaching

**Description:** The core flow of the Simulate module. The Manager creates a campaign → the Simulation Service pushes the email-sending job through the queue (asynchronous to handle high load). Each email embeds a **tracked link/pixel**: when a Learner *opens* or *clicks*, the tracking endpoint records a `sim_event`. The moment a click occurs, the system does two things in parallel: displays the **Just-in-time coaching page** (teaching right at the point of the mistake) and **updates the risk_score**. When coding, ensure tracking is idempotent and does not "punish" the user — the goal is to teach, not to catch mistakes.

```mermaid
sequenceDiagram
    actor M as Manager
    participant GW as API Gateway
    participant SIM as Simulation Service
    participant MQ as Message Queue
    participant WK as Worker (Email)
    participant EG as Email Gateway
    actor L as Learner
    participant TRK as Tracking Endpoint
    participant LRN as Learning Service

    M->>GW: POST /sim/campaigns (template, target, schedule)
    GW->>SIM: Create campaign (status=scheduled)
    SIM->>MQ: enqueue email sending by target
    MQ->>WK: email sending job
    WK->>EG: Send simulation email (link with tracking)
    EG-->>L: Email arrives in inbox

    L->>TRK: Open email -> pixel (action=open)
    TRK->>SIM: POST /sim/events {open}
    L->>TRK: Click link (action=click)
    TRK->>SIM: POST /sim/events {click}
    SIM->>LRN: Trigger lesson auto-enroll
    TRK-->>L: Just-in-time coaching page<br/>(explains the missed signals)
    SIM->>SIM: Update user's risk_score
```

### 6.3. User Reports Phishing Email → AI Classifies → Blacklist Check → Alert Broadcast

**Description:** The "Report & Alert" flow, shown as a swimlane process (each lane = a service/actor that owns the step). The Learner clicks the report button from the extension/add-in → the Reporting Service stores the report → the **AI Service classifies** it (clean/spam/threat + confidence) → checks against the **blacklist**. The **gateway** `confidence >= auto threshold?` is the decision branch: if confidence *exceeds the auto threshold*, the email is mass-quarantined immediately; if uncertain, it goes into the **triage queue** for an Analyst to decide. When confirmed as a real threat, the Notification Service **broadcasts a realtime alert** to the whole organization over WebSocket. This is the "collective intelligence" loop: one person reports, the whole organization is protected.

```mermaid
flowchart TB
    classDef ev fill:#d4edda,stroke:#28a745,color:#155724;

    subgraph L[Learner]
        A([Start: Click 'Report phishing']):::ev
        Z([Receive realtime alert]):::ev
    end

    subgraph EXT[Browser Ext / API Gateway]
        B[POST /reports/phishing]
    end

    subgraph REP[Reporting Service]
        C[Store report status=new]
        D[Request AI classification]
        E[Check blacklist]
        G{confidence >= auto threshold?}
        H[Mass-quarantine email]
        I[Update status + blacklist]
    end

    subgraph AIS[AI Service]
        D2[Classify: label, confidence, reason]
    end

    subgraph BL[Blacklist Store]
        E2[Lookup URL/phone/account]
    end

    subgraph AN[Analyst SOC]
        T[Triage queue review]
        U[POST /reports/id/triage decision]
    end

    subgraph NOT[Notification / WebSocket Hub]
        N[Broadcast alert to whole org]
    end

    A --> B --> C --> D
    D --> D2 --> E
    E --> E2 --> G
    G -- yes --> H --> N
    G -- "no, needs review" --> T --> U --> I
    I -- confirmed threat --> N
    N --> Z
```

### 6.4. Adaptive Learning Framework – Risk-based Auto-enrollment

**Description:** The self-running "adaptive learning" mechanism. On schedule (Scheduler), the Analytics Service **recomputes the risk_score** from sim_events, quiz results, and reporting behavior, then hands it off to **AI Orchestration (AIDA)** to choose the appropriate course/level and **auto-enroll** the user. When the learner completes it, the progress flows back to Analytics to recompute risk — creating a continuous improvement loop. High-risk users are assigned more; strong users get a reduced load.

```mermaid
sequenceDiagram
    participant SCH as Scheduler
    participant ANA as Analytics Service
    participant AIDA as AI Orchestration
    participant LRN as Learning Service
    actor L as Learner

    SCH->>ANA: Periodically recompute risk_score
    ANA->>ANA: Aggregate sim_events, quiz, report
    ANA-->>AIDA: risk_score + weaknesses by domain
    AIDA->>AIDA: Choose appropriate course/level
    AIDA->>LRN: Auto-enroll (adaptive)
    LRN-->>L: Assign new lesson + deadline reminder
    L->>LRN: Complete lesson + quiz
    LRN->>ANA: Update progress -> recompute risk
```

### 6.5. AI Generates Simulated Phishing Templates (with Moderation)

**Description:** How AI helps the Content Editor create templates quickly but safely. The Editor states a request (industry, season, channel) → the AI Service calls the LLM with a prompt *constrained to Vietnamese scenarios* → the draft must pass a **Moderation step** before final human approval. Only templates in the `approved` state can be used for campaigns. The Moderation step is a key control point to prevent AI-generated content from being misused or inappropriate.

```mermaid
sequenceDiagram
    actor E as Content Editor
    participant GW as API Gateway
    participant AIS as AI Service
    participant LLM as LLM API
    participant MOD as Moderation
    participant DB as DB (sim_templates)

    E->>GW: POST /ai/templates/generate (industry, season, channel)
    GW->>AIS: Request content generation
    AIS->>LLM: Prompt constrained to VN scenarios
    LLM-->>AIS: Email/SMS draft
    AIS->>MOD: Moderate content safety
    MOD-->>AIS: pass/flag
    AIS-->>E: Draft for approval
    E->>GW: PATCH approve
    GW->>DB: Store template (status=approved)
```

---

## 7. State Diagrams (Entity Lifecycles)

### 7.1. Phishing Report Lifecycle

**Description:** The states a report passes through, used to design the `status` field of the `reports` table and the transition logic. From `New` → AI classification; then it branches: high confidence goes to `AutoQuarantined`, the rest go to `InReview` for the Analyst to decide `Confirmed` (threat) or `Dismissed` (clean/spam). A confirmed threat goes to `Broadcasted` (alert broadcast) and then `Closed`. This diagram is the source of truth for validity checks on state transitions.

```mermaid
stateDiagram-v2
    [*] --> New: User submits
    New --> AIClassified: AI classifies
    AIClassified --> AutoQuarantined: high confidence (threat)
    AIClassified --> InReview: needs review
    InReview --> Confirmed: Analyst confirms threat
    InReview --> Dismissed: Clean/Spam
    Confirmed --> Broadcasted: Broadcast alert
    AutoQuarantined --> Broadcasted
    Broadcasted --> Closed
    Dismissed --> Closed
    Closed --> [*]
```

### 7.2. Simulation Campaign Lifecycle

**Description:** The states of a Simulate campaign, mapping directly to `sim_campaigns.status`. `Draft` → `Scheduled` (scheduled) → `Running` (sending) → `Collecting` (collecting events) → `Completed` → `Reported` (generate report + auto-enroll lessons for those who fell for the trap). It can be `Cancelled` from Draft/Scheduled. The diagram helps determine when editing/cancelling is allowed and when to open/close the event collection window.

```mermaid
stateDiagram-v2
    [*] --> Draft
    Draft --> Scheduled: Schedule
    Scheduled --> Running: Time to send
    Running --> Collecting: Collecting events
    Collecting --> Completed: Time elapsed
    Completed --> Reported: Generate report + auto-enroll
    Reported --> [*]
    Draft --> Cancelled
    Scheduled --> Cancelled
```

---

## 8. Flowchart – Risk Score Calculation

**Description:** The algorithm for calculating a user's risk score — the input for adaptive learning and smart grouping. The system takes 90 days of data (sim_events, quizzes/enrollments, reporting behavior), applies **weights**: adds points for simulation clicks/submits or for overdue lessons; subtracts points for correct reports or completed lessons. It aggregates & normalizes to a 0–100 scale, then thresholds: `≥70` high risk (reinforced auto-enroll), `40–69` monitor, `<40` maintain. The result is stored in `risk_scores`. This is the business specification for implementing the scoring function (weights/thresholds should be made configurable).

```mermaid
flowchart TD
    Start([Start risk_score calculation]) --> C1[Fetch 90-day sim_events]
    C1 --> C2[Fetch quiz/enrollment results]
    C2 --> C3[Fetch phishing reporting behavior]
    C3 --> W{Compute weights}
    W --> R1[+ points: simulation click/submit]
    W --> R2[- points: correct report, completed lesson]
    W --> R3[+ points: overdue lesson]
    R1 & R2 & R3 --> AGG[Aggregate & normalize 0-100]
    AGG --> SEG{risk_score}
    SEG -->|>= 70| HIGH[High-risk group<br/>reinforced auto-enroll]
    SEG -->|40-69| MED[Monitor]
    SEG -->|< 40| LOW[Maintain]
    HIGH & MED & LOW --> Save[(Store risk_scores)]
    Save --> End([End])
```

---

## 9. REST API Specification

The full machine-readable specification is in the file **`DigiShield_openapi.yaml`** (OpenAPI 3.0 — import into Swagger UI / Postman / generate client SDK). Summary of endpoint groups:

| Group | Representative Endpoint | Method | Description |
|------|--------------------|--------|-------|
| Auth | `/auth/login`, `/auth/sso/callback`, `/auth/refresh` | POST | Internal/SSO login, token refresh |
| Users | `/users`, `/users/{id}` | GET POST PATCH DELETE | User & role management |
| Groups | `/groups`, `/groups/{id}/evaluate` | GET POST | Smart group; re-evaluate members |
| Courses | `/courses`, `/lessons/{id}` | GET POST | Lesson library |
| Enrollments | `/enrollments`, `/enrollments/{id}` | GET POST PATCH | Enrollment & progress |
| Simulation | `/sim/campaigns`, `/sim/events` | GET POST | Simulation campaigns & events |
| Reports | `/reports/phishing`, `/reports/phishing/{id}/triage` | POST GET | Email reporting & triage |
| Alerts | `/alerts/broadcast` | POST | Broadcast alert (WebSocket push) |
| Blacklist | `/blacklist` | GET POST | Blacklist management & sync |
| Analytics | `/analytics/risk`, `/analytics/benchmark` | GET | Risk score, benchmarking |
| AI | `/ai/templates/generate`, `/ai/classify` | POST | Template generation, email classification |
| Export | `/reports/export` | POST | Export compliance report PDF |

**General conventions:**
- Base URL: `https://api.digishield.vn/api/v1`
- Auth: `Authorization: Bearer <JWT>`; scoped by `org_id` in the token.
- Pagination: `?page=&size=`; returns `X-Total-Count`.
- Errors: RFC 7807 standard (`application/problem+json`).
- Idempotency for the POST that creates campaigns: `Idempotency-Key` header.

---

## 10. Event Model (Event/Queue)

The main events flow through the Message Broker (suggested topics):

| Topic | Producer | Consumer | Main Payload |
|-------|----------|----------|---------------|
| `sim.dispatch` | Simulation | Email/SMS Worker | campaign_id, user_id, channel |
| `sim.event.recorded` | Tracking | Analytics | event(open/click/submit), ts |
| `report.submitted` | Reporting | AI Service | report_id, payload |
| `report.classified` | AI Service | Reporting, Notification | label, confidence |
| `alert.broadcast` | Notification | WebSocket Hub | severity, message |
| `risk.recompute` | Scheduler | Analytics | scope, scope_id |
| `enrollment.assigned` | AI Orchestration | Learning, Notification | user_id, course_id |

> Use a **dead-letter queue** for failed sending jobs; retry with backoff. Workers are **idempotent** by `event_id`.

---

## 11. Security & Non-functional Requirements

- **Authentication/Authorization:** short-lived JWT + refresh; RBAC with 6 roles (Super Admin, Org Admin, Manager, Content Editor, Analyst, Learner); scoped by tenant + department.
- **Encryption:** TLS 1.2+ in transit; at-rest encryption for sensitive data; secrets managed via a secrets manager.
- **Privacy:** do not publicly disclose the identity of individuals who "fell for the trap"; anonymous culture surveys; compliance with the Personal Data Protection Decree.
- **Audit:** every sensitive action is logged to `audit_logs` (actor, action, target, ip, ts).
- **Performance:** campaigns to tens of thousands of people sent via queue; dashboard < 2s; Redis cache for analytics.
- **Availability:** target uptime ≥ 99.5%; periodic backups; DR plan.
- **Data sovereignty:** option for in-country / on-premise storage for the government sector.
- **Observability:** centralized logging, metrics, distributed tracing.

---

## 12. 100% Specification Closure Supplement

This section supplements the missing/partial features to fully match the product specification: **gamification, knowledge & culture assessments, ThreatFlip, certificates, bulk import/SCIM, reminders, AI moderation, AI orchestration, coaching content, compliance policy**.

### 12.1. Supplementary Data Entities (Extended ER)

**Description:** The extended ER for advanced features, continuing the model in Section 5. It adds the clusters: *gamification* (badges, user_badges, points_ledger), *assessment* (assessments, assessment_responses — `user_id` nullable to support anonymous surveys), *certificates* (certificates), *notifications/reminders* (notifications), *ThreatFlip* (threat_intel that can be converted into a sim_template), *coaching* (coaching_pages attached to a template), and *compliance* (compliance_policies). These tables all have an `org_id` and integrate with the existing core tables.

```mermaid
erDiagram
    USERS ||--o{ USER_BADGES : earns
    BADGES ||--o{ USER_BADGES : awarded
    USERS ||--o{ POINTS_LEDGER : accrues
    ORGANIZATIONS ||--o{ ASSESSMENTS : owns
    ASSESSMENTS ||--o{ ASSESSMENT_RESPONSES : collects
    USERS ||--o{ ASSESSMENT_RESPONSES : answers
    USERS ||--o{ CERTIFICATES : holds
    COURSES ||--o{ CERTIFICATES : certifies
    ORGANIZATIONS ||--o{ NOTIFICATIONS : sends
    USERS ||--o{ NOTIFICATIONS : receives
    ORGANIZATIONS ||--o{ THREAT_INTEL : collects
    THREAT_INTEL ||--o| SIM_TEMPLATES : converts_to
    SIM_TEMPLATES ||--o{ COACHING_PAGES : has
    ORGANIZATIONS ||--o{ COMPLIANCE_POLICIES : defines

    BADGES {
        uuid id PK
        uuid org_id FK
        string name
        json criteria_json
        string icon_ref
    }
    USER_BADGES {
        uuid id PK
        uuid user_id FK
        uuid badge_id FK
        timestamp awarded_at
    }
    POINTS_LEDGER {
        uuid id PK
        uuid user_id FK
        int points
        string reason
        timestamp ts
    }
    ASSESSMENTS {
        uuid id PK
        uuid org_id FK
        enum type "knowledge|culture|placement"
        boolean anonymous
        json questions_json
        string period
    }
    ASSESSMENT_RESPONSES {
        uuid id PK
        uuid assessment_id FK
        uuid user_id FK "nullable if anonymous"
        json answers_json
        timestamp submitted_at
    }
    CERTIFICATES {
        uuid id PK
        uuid user_id FK
        uuid course_id FK
        string serial
        string pdf_ref
        timestamp issued_at
    }
    NOTIFICATIONS {
        uuid id PK
        uuid org_id FK
        uuid user_id FK
        enum type "reminder|alert|system"
        enum channel "in_app|email|sms"
        json payload
        enum status "scheduled|sent|read"
        timestamp scheduled_at
        timestamp sent_at
    }
    THREAT_INTEL {
        uuid id PK
        uuid org_id FK
        string source "NCSC|user_report|feed"
        text raw_payload
        uuid converted_template_id FK
        timestamp collected_at
    }
    COACHING_PAGES {
        uuid id PK
        uuid org_id FK
        uuid template_id FK
        text content_ref
        json signals_json
    }
    COMPLIANCE_POLICIES {
        uuid id PK
        uuid org_id FK
        string framework "ISO27001|PDPD"
        json mapping_json
        boolean mandatory
        string due_rule
    }
```

### 12.2. Supplementary Endpoint Groups

| Group | Endpoint | Method | Description |
|------|----------|--------|-------|
| Gamification | `/gamification/leaderboard` | GET | Leaderboard by period/unit |
| Gamification | `/users/{id}/badges` | GET | A user's badges |
| Gamification | `/users/{id}/points` | GET | Accrued points (points ledger) |
| Assessment | `/assessments` | GET POST | Create knowledge/culture/placement surveys |
| Assessment | `/assessments/{id}/responses` | POST | Submit responses (supports anonymous) |
| Assessment | `/assessments/{id}/results` | GET | Aggregated results (anonymized) |
| Assessment | `/assessments/placement` | POST | Adaptive placement assessment |
| Certificate | `/users/{id}/certificates` | GET | List of certificates |
| Certificate | `/certificates/{id}` | GET | Download certificate PDF |
| Provisioning | `/users/import` | POST | Bulk import (CSV/JSON) |
| Provisioning | `/scim/v2/Users` | GET POST PUT DELETE | SCIM 2.0 sync from IdP |
| Notification | `/notifications` | GET POST | List & create notifications |
| Notification | `/notifications/reminders` | POST | Schedule mandatory training reminders |
| ThreatFlip | `/threat-intel` | GET POST | Threat intel sources |
| ThreatFlip | `/threat-intel/{id}/convert` | POST | Convert real threat → template/lesson |
| ThreatFlip | `/reports/phishing/{id}/convert-to-training` | POST | "Flip" a reported email into training content |
| AI | `/ai/moderate` | POST | Moderate the safety of AI-generated content |
| AI | `/ai/orchestration/run` | POST | Run AIDA: recompute risk + auto-enroll |
| Coaching | `/coaching-pages` | GET POST | Manage just-in-time coaching content |
| Compliance | `/compliance/policies` | GET POST | Compliance policies & mapping |
| Compliance | `/compliance/status` | GET | Compliance status by unit/individual |

### 12.3. Sequence — Gamification (Awarding Points & Badges)

**Description:** An event-driven reward mechanism. When a reward-worthy event occurs (e.g., a correct report, a completed lesson) and is published to the queue, the **Gamification Service** applies the point rules, writes to the `points_ledger`, then checks the badge criteria. If met, it grants a `user_badge` and sends a notification + updates the leaderboard. The event-driven design decouples gamification from the core business and makes it easy to add new rules.

```mermaid
sequenceDiagram
    participant SRC as Event source<br/>(correct report / completed lesson)
    participant MQ as Message Queue
    participant GAM as Gamification Service
    participant DB as DB (points/badges)
    participant NOT as Notification
    actor L as Learner

    SRC->>MQ: emit event (e.g.: report.confirmed)
    MQ->>GAM: process point rules
    GAM->>DB: + points into points_ledger
    GAM->>GAM: check badge criteria
    alt criteria met
        GAM->>DB: grant user_badge
        GAM->>NOT: notify "You earned a badge!"
        NOT-->>L: Notification + update leaderboard
    end
```

### 12.4. Sequence — ThreatFlip (Turn a Real Attack into a Lesson)

**Description:** The process of "flipping" a real (confirmed) attack email into training content. The Analyst selects a threat email → the ThreatIntel Service asks the AI to **de-identify** it (remove victims' personal information) and generate a simulation template + coaching page → it passes through **moderation** → and is stored as a draft pending approval. This keeps the training content closely aligned with the threats currently in play. Note the privacy compliance at the de-identification step.

```mermaid
sequenceDiagram
    actor AN as Analyst
    participant REP as Reporting Service
    participant TI as ThreatIntel Service
    participant AIS as AI Service
    participant MOD as Moderation
    participant LRN as Learning/Sim

    AN->>REP: Select confirmed threat email
    REP->>TI: POST convert-to-training
    TI->>AIS: De-identify + generate simulation template/lesson
    AIS->>MOD: Moderate content
    MOD-->>AIS: pass
    AIS-->>TI: Draft template + coaching page
    TI->>LRN: Store sim_template + coaching_page (pending approval)
    LRN-->>AN: Ready to use for campaigns
```

### 12.5. Sequence — Anonymous Culture Survey

**Description:** Measure "security culture" while still protecting identity. The Org Admin creates a survey with the flag `anonymous=true`; when a Learner submits responses, the system **does not store the user_id**. Analytics only aggregates by department, with no way to trace back to an individual. The crucial point when coding: separate the anonymous submission channel from identity at the API layer to ensure no linkage is leaked.

```mermaid
sequenceDiagram
    actor A as Org Admin
    participant ASM as Assessment Service
    actor L as Learner
    participant ANA as Analytics

    A->>ASM: POST /assessments (type=culture, anonymous=true)
    ASM-->>L: Invite to survey
    L->>ASM: POST responses (WITHOUT user_id)
    ASM->>ANA: Aggregate anonymously by department
    ANA-->>A: Security culture report (no traceback to individual)
```

---

## 13. WebSocket Specification (Realtime)

The realtime channel for instant alerts & notifications.

- **Endpoint:** `wss://api.digishield.vn/ws?token=<JWT>`
- **Authentication:** JWT in query/handshake; bound to `org_id` to only receive events for the correct tenant.
- **Heartbeat:** ping/pong every 30s; auto-reconnect with backoff.

| Event (server → client) | Payload | Meaning |
|---|---|---|
| `alert.broadcast` | `{ severity, message, source, ts }` | Organization-wide alert |
| `notification.new` | `{ type, payload, ts }` | Personal notification (learning reminder, badge) |
| `campaign.update` | `{ campaign_id, status, stats }` | Campaign progress update (dashboard) |
| `report.status` | `{ report_id, status }` | Report status update for the Analyst |

| Message (client → server) | Payload | Meaning |
|---|---|---|
| `subscribe` | `{ channels: [] }` | Subscribe to channels (alerts, dashboard, etc.) |
| `ack` | `{ event_id }` | Acknowledge receipt of an alert |

---

## 14. Traceability Matrix

Maps **specification → API → screen** to track coverage (Yes = present after the supplement).

| # | Specified Feature | Main API | Screen | Coverage |
|---|---|---|---|---|
| 1 | Learn (lessons, quizzes) | `/courses` `/enrollments` | Library, Learner portal | Yes |
| 2 | Certificates | `/users/{id}/certificates` `/certificates/{id}` | Learner portal | Yes |
| 3 | Multi-channel Simulate | `/sim/campaigns` `/sim/events` | Create/Campaign results | Yes |
| 4 | Just-in-time coaching | `/coaching-pages` + tracking | Coaching page | Yes |
| 5 | Report & triage | `/reports/phishing` `/.../triage` | SOC handling inbox | Yes |
| 6 | Realtime alert | `/alerts/broadcast` + WebSocket | Alert center | Yes |
| 7 | Measure / risk | `/analytics/risk` `/analytics/benchmark` | Dashboard | Yes |
| 8 | AI orchestration (AIDA) | `/ai/orchestration/run` | Admin dashboard | Yes |
| 9 | AI template generation + moderation | `/ai/templates/generate` `/ai/moderate` | Content authoring | Yes |
| 10 | AI email classification | `/ai/classify` | SOC handling inbox | Yes |
| 11 | ThreatFlip | `/threat-intel/{id}/convert` | SOC inbox → Content authoring | Yes |
| 12 | Adaptive learning | `/assessments/placement` `/enrollments` | Learning library | Yes |
| 13 | Gamification | `/gamification/leaderboard` `/users/{id}/badges` | Learner portal | Yes |
| 14 | Knowledge & culture assessment | `/assessments` `/.../responses` `/.../results` | Survey | Yes |
| 15 | Smart Groups | `/groups` `/groups/{id}/evaluate` | Group management | Yes |
| 16 | Benchmark | `/analytics/benchmark` | Reports | Yes |
| 17 | Compliance & audit | `/compliance/policies` `/compliance/status` `/reports/export` | Reports & compliance | Yes |
| 18 | Bulk import / SCIM | `/users/import` `/scim/v2/Users` | User management | Yes |
| 19 | Reminders / notifications | `/notifications` `/notifications/reminders` | Learner portal | Yes |

> **Conclusion:** after the supplement, all 19/19 functional groups in the specification have a corresponding endpoint, data model, and screen — sufficient for end-to-end implementation.

---

## 15. v1.2 Supplement — Proactive Detection & OTT Channels

Adds 3 differentiating features for the Vietnamese context (referencing Hoxhunt/Cofense & the 2026 banking anti-fraud trends): **(1) Real-time scam interception**, **(2) Suspicious recipient account warning**, **(3) Simulation over Zalo/OTT**.

### 15.1. Expanding Simulation Channels (Zalo/OTT)

Expand the `channel` of `sim_templates` / `sim_events`: add `zalo`, `teams`, `slack` alongside `email|sms|qr|usb|voice`. Workers send via the respective APIs (Zalo OA, Microsoft Graph, Slack API). Tracking & coaching reuse the existing mechanism.

### 15.2. Supplementary Data Entities

**Description:** Two tables serving proactive detection. `ACCOUNT_WATCHLIST` stores suspicious recipient accounts/phone numbers/wallets along with a `risk_level` and source (SIMO/NAPAS/A05/user report) — for lookup at the money-transfer point. `INTERVENTION_EVENTS` records each time the system intervenes: the triggering signals (on a call, new payee, large amount, watchlist hit) and the decision (allow/warn/pause/block) for audit and analysis.

```mermaid
erDiagram
    ORGANIZATIONS ||--o{ ACCOUNT_WATCHLIST : maintains
    ORGANIZATIONS ||--o{ INTERVENTION_EVENTS : logs
    USERS ||--o{ INTERVENTION_EVENTS : protected

    ACCOUNT_WATCHLIST {
        uuid id PK
        uuid org_id FK
        enum type "bank_account|phone|wallet"
        string value
        enum risk_level "watch|high|confirmed"
        string source "SIMO|NAPAS|A05|user_report"
        timestamp added_at
    }
    INTERVENTION_EVENTS {
        uuid id PK
        uuid org_id FK
        uuid user_id FK
        json signals "on_call|new_payee|high_amount|watchlist_hit"
        enum decision "allow|warn|pause|block"
        string context_ref
        timestamp ts
    }
```

### 15.3. Supplementary Endpoints

| Group | Endpoint | Method | Description |
|------|----------|--------|-------|
| Interception | `/interventions/evaluate` | POST | Instant risk evaluation at the transaction point (returns allow/warn/pause/block) |
| Interception | `/interventions` | GET | Intervention log (for SOC/reporting) |
| Watchlist | `/account-watchlist` | GET POST | Manage & sync suspicious recipient accounts |
| Watchlist | `/account-watchlist/check` | GET | Quick lookup of an account/phone number at the money-transfer point |

> **Integration:** these endpoints are exposed via an **embedded SDK** (mobile/web) so banking apps, e-wallets, or internal apps can call them at the transfer confirmation step. DigiShield does not execute transactions — it only returns a warning decision.

### 15.4. Sequence — Real-time Scam Interception (active-call detection)

**Description:** The most differentiating feature for the Vietnamese context. At the transfer confirmation step, the app (via the embedded SDK) calls `/interventions/evaluate` with context: the amount, recipient account, *whether the user is currently on a call*, and whether the payee is new. The Intervention Service checks the **watchlist** and combines signals in the **Risk Engine** to reach a decision. If risk is high → it returns `pause` with an educational message ("Is someone instructing you to transfer money over the phone?"), helping the user stay alert against exactly the kind of impersonation scenario (e.g., posing as police). **Important:** DigiShield only *returns a warning decision* — it does not execute or block the transaction; the final decision rests with the banking app and the user. Every intervention is recorded as an `intervention_event`.

```mermaid
sequenceDiagram
    actor U as User
    participant APP as Bank App / DigiShield SDK
    participant INT as Intervention Service
    participant WL as Account Watchlist
    participant RISK as Risk Engine

    U->>APP: Confirm transfer (large amount, new recipient)
    APP->>INT: POST /interventions/evaluate<br/>{amount, dest_account, on_call, new_payee}
    INT->>WL: GET check recipient account
    WL-->>INT: risk_level (watchlist hit?)
    INT->>RISK: Combine signals (on call + new payee + amount + watchlist)
    RISK-->>INT: decision
    alt High risk
        INT-->>APP: pause + educational content
        APP-->>U: "Is someone instructing you to transfer money over the phone?"
        U->>APP: User reconsiders / cancels
    else Safe
        INT-->>APP: allow
    end
    INT->>INT: Record intervention_event
```

### 15.5. Traceability Matrix Update

| # | Feature | Main API | Coverage |
|---|---|---|---|
| 20 | Zalo/OTT simulation | `/sim/campaigns` (channel=zalo/teams/slack) | Yes |
| 21 | Real-time scam interception | `/interventions/evaluate` | Yes |
| 22 | Suspicious recipient account warning | `/account-watchlist` `/account-watchlist/check` | Yes |

> **Summary:** all 22/22 functional groups are now fully specified (data model + API + diagram).

---

## 16. Use Case Diagram (UML)

**Description:** A business-level view — *who* (actor) can do *what* (use case) with the system. Used to lock scope with the customer and for acceptance. Mermaid lacks standard UML use case notation, so the diagram below simulates it with a graph (actors as rectangles outside, use cases as rounded shapes inside the "DigiShield System" block); the table below is the full version.

```mermaid
graph LR
    L([Learner])
    M([Manager])
    OA([Org Admin])
    CE([Content Editor])
    AN([Analyst SOC])
    SA([Super Admin])
    SYS([AI/Scheduler System])

    subgraph DigiShield
        U1(Learn lessons & take quizzes)
        U2(Report phishing email)
        U3(Receive coaching & badges)
        U4(Launch simulation campaign)
        U5(View reports & risk score)
        U6(Manage users & groups)
        U7(Author & approve content)
        U8(Triage reports & broadcast alerts)
        U9(Administer tenants & integrations)
        U10(Auto-enroll & compute risk)
        U11(Evaluate transaction interventions)
    end

    L --- U1
    L --- U2
    L --- U3
    M --- U4
    M --- U5
    OA --- U5
    OA --- U6
    CE --- U7
    AN --- U8
    AN --- U2
    SA --- U9
    SYS --- U10
    SYS --- U11
```

**Full use case table:**

| Actor | Main Use Cases |
|---|---|
| Learner | Learn lessons & quizzes · Report phishing email · Receive coaching/badges · Participate in surveys · Receive realtime alerts |
| Manager | Launch simulation campaigns · View department risk · Track training completion |
| Org Admin | Manage users/groups · Configure compliance policies · Import/SCIM · Export reports |
| Content Editor | Author lessons/simulation templates · Request AI content generation · Submit for moderation |
| Analyst SOC | Triage reports · Manage blacklist/watchlist · Broadcast organization-wide alerts · ThreatFlip |
| Super Admin | Administer tenants · Configure global integrations |
| System (AI/Scheduler) | Compute risk score · Adaptive auto-enroll · Evaluate transaction interventions · Award gamification |

---

## 17. BPMN Process Diagrams

**Description:** Three backbone business processes modeled in BPMN (multiple roles, decision gateways). The real BPMN 2.0 XML files are included (open with [bpmn.io](https://demo.bpmn.io) or Camunda Modeler):
- `DigiShield_bpmn_incident_response.bpmn` — Report handling & alert broadcast
- `DigiShield_bpmn_content_approval.bpmn` — AI content approval
- `DigiShield_bpmn_sim_campaign.bpmn` — Simulation campaign

Below is a quick-view (Mermaid) equivalent of each process.

### 17.1. Report Handling & Alert Broadcast (Incident Response)

```mermaid
flowchart LR
    S([User reports]) --> T1[Store report]
    T1 --> T2[AI classify + blacklist check]
    T2 --> T3[Analyst triage]
    T3 --> G{Is it a threat?}
    G -->|Yes| T4[Broadcast organization-wide alert]
    T4 --> E1([Done])
    G -->|No| E2([Close - clean/spam])
```

### 17.2. AI Content Approval (Content Approval)

```mermaid
flowchart LR
    S([Content generation request]) --> T1[AI generates content]
    T1 --> T2[Automatic moderation]
    T2 --> T3[Final approver]
    T3 --> G{Approve?}
    G -->|Pass| T4[Publish - approved]
    T4 --> E1([Ready to use])
    G -->|Fail| E2([Return for revision])
```

### 17.3. Simulation Campaign (Simulation Campaign)

```mermaid
flowchart LR
    S([Create campaign]) --> T1[Configure & schedule]
    T1 --> T2[Send simulation via queue]
    T2 --> T3[Collect open/click events]
    T3 --> G{Fell for the trap?}
    G -->|Yes| T4[Auto-enroll lesson + coaching]
    G -->|No| T5[Aggregate report]
    T4 --> T5
    T5 --> E([End])
```

---

## 18. Data Flow Diagram (DFD)

**Description:** A data flow diagram supporting privacy & security assessment (assisting a DPIA under the Personal Data Protection Decree). Notation: rectangle = *external entity*, rounded shape = *processing process*, cylinder = *data store*; labeled arrows are *data flows* (the type of data moving).

### 18.1. DFD Level 0 (context)

```mermaid
graph LR
    U[User]
    IDP[SSO IdP]
    NCSC[NCSC / Blacklist]
    LLM[LLM API]
    GW[Email/SMS Gateway]

    P((DigiShield<br/>System))

    U -->|Learning info, email reports| P
    P -->|Lessons, alerts, coaching| U
    IDP -->|Identity, attributes| P
    P <-->|Account/URL blacklist| NCSC
    P -->|De-identified content| LLM
    LLM -->|Classification content/labels| P
    P -->|Simulation email/SMS| GW
```

### 18.2. DFD Level 1 (main processes & data stores)

```mermaid
graph TB
    U[User]
    AN[Analyst]

    P1((1.0 Authentication & authorization))
    P2((2.0 Training & assessment))
    P3((3.0 Simulation & tracking))
    P4((4.0 Reporting & classification))
    P5((5.0 Analytics & risk))

    D1[(Users/RBAC)]
    D2[(Content & learning progress)]
    D3[(Simulation events)]
    D4[(Reports & blacklist)]
    D5[(Risk score & audit)]

    U -->|Log in| P1 --> D1
    U -->|Learn/quiz| P2 --> D2
    U -->|Interact with simulation email| P3 --> D3
    U -->|Submit suspicious email| P4 --> D4
    AN -->|Triage| P4
    P3 -->|Trap-falling behavior| P5
    P2 -->|Learning results| P5
    P4 -->|Threat label| P5
    P5 --> D5
    P5 -->|Auto-enroll| P2
```

> **Security/privacy note:** sensitive personal data is concentrated in D1, D4 (report payload), and D5 (audit). At-rest encryption, role-based access control, de-identification before sending to the LLM, and a separate anonymous channel for culture surveys (no user_id recorded) are required.

---

## 19. Multi-tenant SaaS Architecture

DigiShield serves many organizations (tenants) on the same platform. This chapter specifies how to **isolate, operate, and monetize** under a multi-tenant SaaS model — a decisive factor for scalability, security, and compliance.

### 19.1. Three Data Isolation Models

```mermaid
graph TB
    subgraph POOL["Pool — shared DB + shared schema"]
        direction LR
        PA[(1 DB · 1 schema)]
        PA --- PR["Separated by tenant_id + Row-Level Security"]
    end
    subgraph BRIDGE["Bridge — shared DB + separate schema"]
        direction LR
        BA[(1 DB)]
        BA --- BS1[schema_tenantA]
        BA --- BS2[schema_tenantB]
    end
    subgraph SILO["Silo — separate infrastructure/DB (or on-premise)"]
        direction LR
        SA[(DB tenant A)]
        SB[(DB tenant B)]
    end
```

| Model | Isolation | Cost/unit | Suitable for |
|---|---|---|---|
| **Pool** (RLS) | Logical (soft) | Lowest | Schools (very many users, low price) |
| **Bridge** (schema/tenant) | Medium | Medium | General enterprises |
| **Silo** (separate DB/infrastructure, on-prem) | Physical (strong) | High | Government agencies, large enterprises, data sovereignty requirements |

> Recommendation: **one codebase**, configurable across all three "deployment tiers". Default to Pool+RLS for new customers; upgrade to Bridge/Silo based on plan & compliance requirements.

### 19.2. Segment → Deployment Tier Matrix

| Segment | Default Tier | Data residency | Upgrade option |
|---|---|---|---|
| Schools | Pool (domestic cloud) | In-country | Bridge at large scale |
| Enterprises | Bridge (cloud) | In-country | Silo/BYOK for enterprise |
| Government Agencies | Silo / On-premise | Mandatory in-country | Air-gapped as required |

### 19.3. Enforcing Isolation at the Data Layer (RLS)

For the Pool model, do **not** rely solely on the application layer filtering by `tenant_id` — **PostgreSQL Row-Level Security** must be enabled so the DB itself blocks cross-tenant access:

```sql
ALTER TABLE reports ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON reports
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
-- Each request sets: SET app.tenant_id = '<from JWT>';
```

- Every business table has a `tenant_id` column (= the current `org_id`), indexed.
- The DB connection uses a *non-superuser* role so that RLS takes effect.
- Mandatory test: an automated test suite verifies that "tenant A cannot read tenant B's data".

### 19.4. Propagating Tenant Context

```mermaid
sequenceDiagram
    participant C as Client
    participant GW as API Gateway
    participant MW as Tenant Middleware
    participant SVC as Service
    participant DB as PostgreSQL (RLS)

    C->>GW: Request + JWT (contains tenant_id)
    GW->>MW: Validate JWT, extract tenant_id
    MW->>MW: Create "tenant context" for the request
    MW->>SVC: Call service with context
    SVC->>DB: SET app.tenant_id = <tenant_id>; query
    DB-->>SVC: Return only the correct tenant's data (RLS)
```

The tenant context must be attached consistently at every storage layer:
- **Cache (Redis):** key prefix `t:{tenant_id}:...`.
- **Queue:** include `tenant_id` in the message; the worker sets the context when processing.
- **Object storage:** bucket/prefix by tenant.
- **Log/metrics/trace:** label with `tenant_id`.

### 19.5. Tenant Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Provisioning: Register / create tenant
    Provisioning --> Configuring: Configure SSO/SCIM, branding
    Configuring --> Seeding: Seed default content (VN scenarios)
    Seeding --> Active: Activate
    Active --> Suspended: Overdue / violation
    Suspended --> Active: Restore
    Active --> Offboarding: Contract ends
    Offboarding --> Exported: Export data for customer
    Exported --> Purged: Delete per Personal Data Protection Decree
    Purged --> [*]
```

- **Offboarding** must support *exporting all data* and *permanent deletion* in accordance with the Personal Data Protection regulations.

### 19.6. Per-tenant Customization

| Item | How |
|---|---|
| Branding | Logo, colors, subdomain (`tenantA.digishield.vn`) |
| Content | Own lesson/template library + inherit the shared library |
| Policy | Risk thresholds, mandatory training, default language |
| Features | **Feature flags** by plan (enable/disable deepfake sim, ThreatFlip, intervention SDK, etc.) |

### 19.7. Resource Isolation & Anti "Noisy Neighbor"

- **Per-tenant rate-limit & quota** at the API Gateway (number of requests, number of campaigns, emails/month).
- **Queue partitioning** or fair weighting so that one tenant's bulk sending does not congest another tenant.
- **Metering** of every consumed resource (emails/SMS sent, AI calls, storage) for billing & over-limit alerting.

### 19.8. Security & Data Sovereignty

- **Data residency:** in-country storage; Silo/on-prem for government agencies.
- **BYOK (bring-your-own encryption key):** enterprise/gov manage their own keys; per-tenant encryption.
- **Per-tenant audit:** `audit_logs` filtered by `tenant_id`; tenant admins see only their own logs; Super Admin sees cross-tenant in a controlled manner.
- **Secret isolation:** secrets/SSO config separated per tenant in the secrets manager.

### 19.9. Billing & Metering

- Plans by segment: per-seat (enterprise), per-project/unit (government), per-student (schools).
- Tie **subscription + plan + feature flags**; metering (section 19.7) is the source for invoices & plan limits.

### 19.10. Supplementary Data Model for Multi-tenant

```mermaid
erDiagram
    TENANTS ||--o{ ORGANIZATIONS : maps
    TENANTS ||--|| SUBSCRIPTIONS : has
    PLANS ||--o{ SUBSCRIPTIONS : defines
    TENANTS ||--o{ FEATURE_FLAGS : toggles
    TENANTS ||--o{ TENANT_SETTINGS : configures
    TENANTS ||--o{ USAGE_METERING : meters

    TENANTS {
        uuid id PK
        string name
        enum tier "pool|bridge|silo"
        string data_region
        enum status "provisioning|active|suspended|offboarding"
    }
    PLANS {
        uuid id PK
        string name "edu|business|gov"
        json limits "seats, emails, ai_calls"
        json features
    }
    SUBSCRIPTIONS {
        uuid id PK
        uuid tenant_id FK
        uuid plan_id FK
        enum status "trial|active|past_due|canceled"
        date renews_at
    }
    FEATURE_FLAGS {
        uuid id PK
        uuid tenant_id FK
        string key
        boolean enabled
    }
    TENANT_SETTINGS {
        uuid id PK
        uuid tenant_id FK
        json branding
        json policy
        string default_locale
    }
    USAGE_METERING {
        uuid id PK
        uuid tenant_id FK
        string metric "email_sent|sms_sent|ai_call|storage"
        bigint value
        date period
    }
```

> Note: `tenant_id` replaces/is unified with `org_id` in the business tables in Sections 5 & 12. One `tenant` can map to one or more `organizations` (e.g., a corporation with multiple units).

### 19.11. Implementation Guide (engineering · infrastructure · runbook)

> Implementation/deployment companion to the design above (§19.1–19.10). Stack per **ADR-002: Java + Spring Boot** (realized on Java 25 + Spring Boot 4.1.0; the Java/Spring tenant-context code is in the [Realization Appendix §A5](DigiShield_ADR.md#a5-tenant-context-in-spring) of `DigiShield_ADR.md`). The snippets below are pseudo-code illustrating the concepts, applied equivalently in Spring.

#### 19.11.1. Principles & Model Selection

| Principle | Meaning |
|---|---|
| Isolate at the DB layer, not just the app layer | Enable RLS so the DB itself blocks cross-tenant leakage, even when the code has a bug |
| `tenant_id` is a first-class citizen | Every business table, every cache/queue/storage key, every log/metric |
| One codebase, multiple tiers | Pool (default) → Bridge → Silo/on-prem; chosen by segment & compliance |
| Fail-closed | Missing tenant context ⇒ reject the request, return no data |

**Tier selection by segment:** Schools → Pool; Enterprises → Bridge; Government Agencies → Silo/On-prem (see §19.1–19.2).

#### 19.11.2. Data Layer — PostgreSQL Row-Level Security

**tenant_id column + index** — every business table:

```sql
ALTER TABLE reports ADD COLUMN tenant_id uuid NOT NULL;
CREATE INDEX idx_reports_tenant ON reports (tenant_id);
```

**Enable RLS + policy:**

```sql
ALTER TABLE reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE reports FORCE ROW LEVEL SECURITY;   -- also applies to the table owner

CREATE POLICY tenant_isolation ON reports
  USING       (tenant_id = current_setting('app.tenant_id', true)::uuid)
  WITH CHECK  (tenant_id = current_setting('app.tenant_id', true)::uuid);
```

- `USING` blocks READS; `WITH CHECK` blocks WRITES of wrong-tenant data.
- `current_setting('app.tenant_id', true)`: the `true` parameter avoids an error when not set — but combined with fail-closed at the app (§19.11.3).

**Non-superuser DB role** — RLS is bypassed for superuser/owner unless FORCE is used. The application must connect using a regular role:

```sql
CREATE ROLE app_user LOGIN PASSWORD '***';
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_user;
```

**Set the tenant per transaction (note connection pooling)** — because the pool reuses connections, do **not** use `SET` (session); use `SET LOCAL` within a **transaction**:

```sql
BEGIN;
SET LOCAL app.tenant_id = '6f1c...';   -- only lives within this transaction
-- ... queries ...
COMMIT;
```

> **PgBouncer:** use *transaction pooling* + `SET LOCAL` (safe). Avoid session-level `SET` because the connection is reused for another tenant.

**Migration per tier:**

| Tier | How to run migration |
|---|---|
| Pool | 1 shared schema → run once |
| Bridge | Iterate over each tenant schema (`SET search_path`) → run sequentially, record the version per schema |
| Silo | Each DB/tenant runs independently within that tenant's deployment pipeline |

#### 19.11.3. Tenant Context at the Application Layer

The access token contains the claim `tid` (tenant_id) + `org`, `role`. Authentication happens at the Gateway; the service trusts the already-verified token.

Middleware that extracts & stores the context (illustration — the Java/Spring version is in the [Realization Appendix §A5](DigiShield_ADR.md#a5-tenant-context-in-spring)):

```ts
// tenant-context.ts
import { AsyncLocalStorage } from 'async_hooks';
export const tenantStore = new AsyncLocalStorage<{ tenantId: string }>();
export const currentTenant = () => {
  const ctx = tenantStore.getStore();
  if (!ctx?.tenantId) throw new ForbiddenException('Missing tenant context'); // fail-closed
  return ctx.tenantId;
};

// tenant.middleware.ts
@Injectable()
export class TenantMiddleware implements NestMiddleware {
  use(req: Request, _res: Response, next: () => void) {
    const tenantId = req.user?.tid;            // already decoded from the JWT
    if (!tenantId) throw new ForbiddenException('No tenant');
    tenantStore.run({ tenantId }, () => next());
  }
}
```

Apply the tenant to every DB query — wrap each request/transaction to set `app.tenant_id`:

```ts
async function withTenant<T>(db: DataSource, fn: (m: EntityManager) => Promise<T>) {
  return db.transaction(async (m) => {
    await m.query(`SET LOCAL app.tenant_id = $1`, [currentTenant()]);
    return fn(m);
  });
}
```

> Principle: do **not** allow DB queries outside `withTenant` (lint/review blocks them). This is the last line of defense alongside RLS.

#### 19.11.4. Propagating the Tenant to Cache, Queue, Storage

| Infrastructure | Convention |
|---|---|
| Redis | Key prefix: `t:{tenantId}:user:{id}` ; scan/delete by prefix on offboard |
| Message queue | Add `tenant_id` to the header/payload; the worker runs `SET LOCAL` before processing; consider a dedicated queue/topic per large tenant |
| Object storage | Prefix/bucket: `s3://digishield/{tenantId}/reports/...` ; IAM policy by prefix |
| Log / Metrics / Trace | Tag with `tenant_id` (but do NOT log sensitive personal data) |

Sample worker:

```ts
queue.process(async (job) => {
  await tenantStore.run({ tenantId: job.data.tenant_id }, async () => {
    await withTenant(db, (m) => handle(job, m));
  });
});
```

#### 19.11.5. Tenant Provisioning

```mermaid
flowchart TD
  A[Create tenant record] --> B{Tier?}
  B -->|Pool| C[Use shared schema]
  B -->|Bridge| D[Create dedicated schema + run migration]
  B -->|Silo| E[Provision dedicated DB/infrastructure]
  C --> F[Create default TenantSettings]
  D --> F
  E --> F
  F --> G[Enable feature flags per Plan]
  G --> H[Seed VN content + default courses]
  H --> I[Configure SSO/SCIM]
  I --> J[Create the first Org Admin + send invite]
  J --> K[Status = active]
```

Provisioning script (condensed):

```ts
async function provisionTenant(input) {
  const tenant = await createTenant({ name: input.name, tier: input.tier, region: 'vn', status: 'provisioning' });
  if (input.tier === 'bridge') await createSchemaAndMigrate(tenant.id);
  if (input.tier === 'silo')   await provisionDedicatedDb(tenant.id);   // IaC: Terraform/Helm
  await seedDefaultSettings(tenant.id);
  await applyPlanFeatureFlags(tenant.id, input.planId);
  await seedDefaultContent(tenant.id);            // VN fraud scenarios, basic courses
  await configureSso(tenant.id, input.sso);
  await inviteOrgAdmin(tenant.id, input.adminEmail);
  await setTenantStatus(tenant.id, 'active');
  return tenant;
}
```

Corresponding API: `POST /tenants`, `PATCH /tenants/{id}`, `PATCH /tenants/{id}/feature-flags` (see OpenAPI).

#### 19.11.6. Feature Flags & Per-tenant Configuration

- Load `feature_flags` + `tenant_settings` when initializing a session; cache by `t:{tenantId}:flags` (short TTL + invalidation on change).
- Check flags on both the backend (block the API) and the frontend (hide the UI).

```ts
if (!await flags.enabled('deepfake_sim')) throw new ForbiddenException('Feature off for tenant');
```

- `tenant_settings`: branding (logo/color/subdomain), policy (risk threshold, mandatory training), `default_locale`.

#### 19.11.7. Infrastructure Deployment per Tier

**Pooled cloud (default — schools, SMB):** one K8s cluster, one shared set of services, one DB (Pool + RLS); logical tenant separation; scale out (HPA) as load grows.

```yaml
# helm values (condensed)
deployment:
  replicas: 3
database:
  mode: pool          # shared db + RLS
  url: postgres://app_user@pg-primary/digishield
multitenancy:
  defaultTier: pool
  rls: true
```

**Bridge / Dedicated (enterprise):** shared cluster but a dedicated schema per tenant; or a dedicated namespace/DB for large customers. Same Helm chart, different `values` (mode: bridge, schema per tenant).

**Silo / On-premise / Air-gapped (government):** install the full suite within internal infrastructure; data kept in-country. Package images offline (internal registry), Helm chart + dedicated DB; disable unnecessary outbound calls, use an internal blacklist source synced periodically.

```yaml
multitenancy:
  defaultTier: silo
deployment:
  airgapped: true
  imageRegistry: registry.internal.gov.vn/digishield
```

**Tenant routing:** subdomain `tenantA.digishield.vn` or header → the Gateway maps to `tenant_id`. For Silo: a dedicated DNS/endpoint per agency.

#### 19.11.8. CI/CD & Multi-tenant Migration

- Pipeline: build → test (including tenant isolation test, §19.11.11) → deploy staging → migration → smoke test → deploy prod.
- **Safe migration (zero-downtime):** expand → migrate → contract (add nullable columns first, backfill, then add constraints/drop the old).
- Bridge: the migration job iterates over all schemas; records the version per tenant; can run in batches (canary a few tenants first).
- Silo: migration lives in each tenant's deployment pipeline; controlled rollout.

```bash
# example: run migration for every schema (bridge)
for schema in $(psql -tAc "select schema_name from tenants_schema"); do
  psql -c "SET search_path=$schema" -f migrations/2026_06_27_add_table.sql
done
```

#### 19.11.9. Data Residency & BYOK

- **Residency:** by default stored in-country; with Silo/on-prem, data never leaves the organization's infrastructure.
- **BYOK (envelope encryption):** each tenant has one Data Encryption Key (DEK) wrapped by a Key Encryption Key (KEK) in the tenant's KMS/HSM. Sensitive data (report payloads, PII) is encrypted with the DEK.
- Rotate keys periodically; revoking the KEK ⇒ invalidates the tenant's data (useful at offboard).

#### 19.11.10. Per-tenant Metering, Quota & Observability

- **Metering:** emit usage events (`email_sent`, `sms_sent`, `ai_call`, `storage`) → aggregate into `usage_metering` per period → the source for invoices & over-limit alerts (`GET /tenants/{id}/usage`).
- **Quota & rate-limit:** configured per Plan; block/advise when limits are reached; partition queues to avoid the "noisy neighbor" problem.
- **Observability:** every log/metric/trace is tagged with a `tenant_id` label; dashboards filter by tenant; per-tenant alerts (e.g., a sudden spike in error rate for one tenant).

#### 19.11.11. Tenant Isolation Testing

Mandatory in CI — this is the "safety net" for RLS:

```ts
it('tenant A cannot read tenant B data', async () => {
  const a = await seedTenantWithReport('A');
  const b = await seedTenantWithReport('B');

  const asA = await withTenantCtx(a.id, () => repo.find());     // set app.tenant_id = A
  expect(asA.map(r => r.tenant_id)).toEqual([a.id]);            // sees only A
  expect(asA.find(r => r.tenant_id === b.id)).toBeUndefined();  // does NOT see B
});

it('rejected when there is no tenant context', async () => {
  await expect(repo.find()).rejects.toThrow('Missing tenant context'); // fail-closed
});
```

Additional: test that writing to the wrong tenant is blocked by `WITH CHECK`; test that cache/queue prefixes do not mix.

#### 19.11.12. Operations Runbook

**Provision a tenant:** `POST /tenants` (name, tier, plan) → create a `provisioning` record → (Bridge/Silo) create the schema/DB + migrate → seed settings, feature flags, content; configure SSO/SCIM → invite the Org Admin; switch to `status=active`.

**Suspend / Resume:** suspend (overdue/violation) via `PATCH /tenants/{id}` `status=suspended` → block login, keep data intact; resume with `status=active`.

**Offboard (contract termination):**
1. Set `status=offboarding` (read-only).
2. **Export data** to the customer (standard format, with integrity checks).
3. After the retention period → **permanently delete** (purge) per the Personal Data Protection Decree: delete DB records, object storage (tenant prefix), cache (prefix), and backups per the retention policy; revoke the KEK (BYOK).
4. Record all offboard operations in `audit_logs`.

Offboard checklist:
- [ ] Notify & confirm with the customer.
- [ ] Export + hand over the data.
- [ ] Delete the DB/schema (per tier), storage prefix, cache prefix.
- [ ] Revoke/destroy the encryption keys.
- [ ] Remove the SSO/SCIM configuration, DNS/subdomain.
- [ ] Save proof of completion (audit).

#### 19.11.13. Security & Compliance Checklist

- [ ] RLS enabled + FORCE on every business table; the app uses a non-superuser role.
- [ ] Every query goes through `withTenant`; lint blocks context-less queries.
- [ ] Fail-closed when the tenant context is missing.
- [ ] Cache/queue/storage all carry the tenant prefix.
- [ ] Tenant isolation tests run in CI and are a merge-blocking condition.
- [ ] Personal data encrypted at-rest; BYOK for enterprise/gov.
- [ ] Data residency in-country; on-prem for the government sector.
- [ ] Audit logs separated per tenant; offboard includes export + compliant purge.
- [ ] Quota/rate-limit per Plan; accurate metering for billing.

---

*Document for development purposes. The KnowBe4 and Proofpoint feature/product names belong to their respective companies and are cited for reference only. The thresholds/timings are suggestions, to be adjusted to actual conditions.*
