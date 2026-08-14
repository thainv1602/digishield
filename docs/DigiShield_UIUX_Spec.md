# DIGITAL SHIELD (DigiShield) — UI/UX Specification

> Version 1.0 · 27/06/2026
> Based on **DigiShield_Technical_Design.md** (screens in Section 10, APIs in `DigiShield_openapi.yaml`).
> Scope: 3 primary user groups — **Admin/Manager**, **Learner**, **SOC Analyst** — plus a shared design system.

---

## Table of Contents
1. [Design Principles](#1-design-principles)
2. [Design System (tokens)](#2-design-system-tokens)
3. [Component Library](#3-component-library)
4. [App Shell & Navigation](#4-app-shell--navigation)
5. [Admin/Manager Group](#5-adminmanager-group)
6. [Learner Group](#6-learner-group)
7. [SOC Analyst Group](#7-soc-analyst-group)
8. [Realtime, Notifications & States](#8-realtime-notifications--states)
9. [Responsive & Accessibility](#9-responsive--accessibility)
10. [Screen ↔ API Map](#10-screen--api-map)
11. [Full Screen Additions (Phase 2)](#11-full-screen-additions-phase-2)

---

## 1. Design Principles

- **Trustworthy & calm:** as a security product, the interface is clean, low-noise, and uses alert colors deliberately (red/amber only for real risk).
- **Non-punitive, learning-oriented:** when a user "falls for" a simulation, the language is positively instructive and does not assign blame.
- **Instant measurement visibility:** every role immediately sees the metrics that matter most to them (risk score, queue, lessons to complete).
- **Localization:** Vietnamese by default, VN-formatted figures, simple easy-to-understand content (especially for the education/government sector).
- **Action-first:** each screen has one clear primary action.
- **Accessible (a11y):** contrast meets WCAG AA, full keyboard operation, and information is never conveyed by color alone.

---

## 2. Design System (tokens)

### 2.1. Colors

| Token | Code | Used for |
|---|---|---|
| `--navy` | `#12284B` | Sidebar, headings, dark accent backgrounds |
| `--blue` | `#2E75B6` | Primary action, links, charts |
| `--teal` | `#0E7C66` | Safe/success states |
| `--red` | `#C00000` | Threats, high risk, dangerous actions |
| `--amber` | `#B26A00` | Warnings, medium risk |
| `--bg` | `#F7F9FC` | Page background |
| `--surface` | `#FFFFFF` | Cards, tables, panels |
| `--border` | `#E2E8F0` | Borders, dividers |
| `--text` | `#1F2937` | Primary text |
| `--muted` | `#6B7280` | Secondary text, labels |

**Risk Score color scale:** `0–39` green `#2E7D32` · `40–69` amber `#B26A00` · `70–100` red `#C00000`.

### 2.2. Typography

Font: **Inter** (fallback Arial). Scale: Display 28/600 · H1 22/600 · H2 18/600 · H3 16/600 · Body 14/400 · Small 12/400 · Mono (code/logs) 13 JetBrains Mono.

### 2.3. Spacing, Radius, Shadow

- Spacing scale (px): `4 · 8 · 12 · 16 · 24 · 32 · 48`.
- Radius: cards `12`, button/input `8`, pill `999`.
- Shadow: `sm` (0 1 2 rgba(0,0,0,.06)) for cards; `md` (0 4 12 rgba(0,0,0,.10)) for modal/popover.
- Grid: content max width `1280px`, 12 columns, 24 gutter.

---

## 3. Component Library

| Component | Description & variants |
|---|---|
| **Button** | `primary` (blue fill), `secondary` (outline), `danger` (red), `ghost` (text only). States: default/hover/disabled/loading |
| **Status pill** | Rounded label: safe (teal), warning (amber), threat (red), neutral (gray) |
| **KPI tile** | Metric card: label + large number + delta (up/down) + small sparkline |
| **Risk gauge** | Semicircular 0–100 gauge, color shifts by threshold |
| **Data table** | Sort, filter, pagination, multi-row selection; status column uses pills |
| **Chart** | Line (trend), funnel (simulation funnel), bar (benchmarking) |
| **Alert banner** | Realtime alert strip at top of page; by severity (info/warning/critical) |
| **Toast** | Bottom-right notification; auto-dismiss; with a "View" button |
| **Modal / Drawer** | Centered dialog / right-sliding panel for details |
| **Stepper** | Step bar for the campaign creation wizard |
| **Badge** | Gamification badge (icon + name) |
| **Empty / Loading / Error state** | Every list must have these 3 states |
| **Form controls** | Input, select, date-time, toggle, file upload (CSV import) |

---

## 4. App Shell & Navigation

General layout: **Left sidebar** (role-based navigation) + **Topbar** (organization, search, realtime notification bell, profile) + **content area**.

```
┌──────────────────────────────────────────────────────────────────────┐
│ [=] DigiShield        Search...            Org v    Bell(3)   User v   │  <- Topbar
├───────────────┬──────────────────────────────────────────────────────┤
│  - Overview   │                                                        │
│  - Training   │   << Content area (per screen) >>                      │
│  - Simulation │                                                        │
│  - Reports    │                                                        │
│  - Alerts     │                                                        │
│  - Users      │                                                        │
│  - Compliance │                                                        │
│  ───────────  │                                                        │
│  - Settings   │                                                        │
└───────────────┴──────────────────────────────────────────────────────┘
```

**Role-based menu** (sidebar auto-filters by RBAC):

| Item | Admin | Manager | Analyst | Learner |
|---|:--:|:--:|:--:|:--:|
| Overview (Dashboard) | x | x | x | – |
| Training | x | x | – | x (learning portal) |
| Simulation | x | x | – | – |
| Reports & alerts | x | – | x | – |
| Users & groups | x | – | – | – |
| Compliance | x | – | – | – |
| Learner portal | – | – | – | x |

---

## 5. Admin/Manager Group

### 5.1. Admin Dashboard

Goal: grasp the overall risk picture within 5 seconds.

```
┌── Overview ──────────────────────────────────────────────────────────────┐
│  ┌── Risk Score ──┐ ┌ Phish-prone % ┐ ┌ Training done ┐ ┌ Open alerts ┐    │
│  │       62       │ │   8.4%  -2.1  │ │   91% +5      │ │    3 threat  │    │
│  │   (Average)    │ │  (vs industry)│ │               │ │              │    │
│  └────────────────┘ └───────────────┘ └───────────────┘ └──────────────┘    │
│                                                                            │
│  ┌── Risk trend (90 days) ────────────────┐ ┌── Industry benchmark ───┐    │
│  │   risk line chart over time            │ │  ### You   # Industry avg │    │
│  └─────────────────────────────────────────┘ └─────────────────────────┘   │
│                                                                            │
│  ┌── High-risk departments ─────┐ ┌── Recent reports ──────────────────┐   │
│  │ Accounting     78 (high)     │ │ - Email "account locked"  threat   │   │
│  │ Sales          64            │ │ - SMS prize win           spam     │   │
│  │ Administration 41            │ │ - Internal email          clean    │   │
│  └──────────────────────────────┘ └────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────────┘
```

- **KPI tiles:** Risk Score (gauge), Phish-prone % (with industry benchmark), Training completion, Open alerts.
- **Data sources:** `GET /analytics/risk`, `GET /analytics/benchmark`, `GET /reports/phishing?status=new`.
- **Interactions:** click a department → filter the dashboard; click a report → open detail drawer.
- **States:** loading skeleton per tile; empty "Not enough data" for new organizations.

### 5.2. Create Simulation Campaign (Wizard)

```
  Steps:  1.Channel ── 2.Template ── 3.Audience ── 4.Schedule ── 5.Preview
┌─────────────────────────────────────────────────────────────────────────┐
│  2. Select simulation template                         [Back]  [Next]    │
│  Selected channel: Email                                                 │
│  ┌─ Template ──────────────────┐  ┌─ Preview ─────────────────────────┐  │
│  │ ( ) "Bank account locked"   │  │  From: no-reply@bank-vn.support    │  │
│  │ (x) "Tuition refund"        │  │  Subject: Tuition refund notice    │  │
│  │ ( ) "Delivery failed"       │  │  [email content rendered...]       │  │
│  │ + Create with AI            │  │  Difficulty: Medium                │  │
│  └─────────────────────────────┘  └────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

- **5 steps:** Channel (email/sms/qr/zalo/teams…) → Template (select or *Create with AI* `POST /ai/templates/generate`) → Audience (select group/smart group) → Schedule (set timing) → Preview & launch.
- **Sources:** `GET /courses`,`/groups`, `POST /sim/campaigns` (with `Idempotency-Key`).
- **Rules:** the "Launch" button only enables once all steps are complete; warn if the audience exceeds X people.

### 5.3. Campaign Results

```
┌── Campaign: "Tuition refund" ───────────────────── Status: Completed ──────────┐
│  Funnel:  Sent 1,000 -> Opened 540 -> Clicked 132 -> Submitted 41               │
│           ##########   ######          ###            #                         │
│  ── Positive outcome ─────────────────────────────────────────────────────       │
│  Reported 88 (8.8% of sent)  ####                                               │
│  Fail rate: 4.1% down  |   Auto-enrolled 41 people in a lesson                   │
│  ┌── List ─────────────────────────────────────────────────────────────────┐    │
│  │ User           Department  Action      Trained?                          │    │
│  │ Nguyen A       Accounting  Clicked     In progress                       │    │
│  │ Tran B         Sales       Reported    -                                 │    │
│  └──────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

- **Funnel** is the failure path only — sent→open→click→submit — where each stage
  is a subset of the one before it, so the bars only ever shrink.
- **Reporting is a separate outcome, not a fifth stage.** Somebody can report a
  simulation without ever opening it, so "reported" is not a subset of
  "submitted": the numbers above (41 submitted, 88 reported) would draw a funnel
  that widens at the bottom. It is measured against *sent*, which is a real
  denominator, and shown below a divider so it never reads as a later stage.
- Individuals who fail are **not publicly displayed** beyond the scope of management.
- **Source:** `GET /sim/campaigns/{id}` + `sim_events`.

### 5.4. Users & Groups (Admin)

- User table (email, role, department, risk score pill), filters, **CSV / SCIM Import** (`POST /users/import`).
- "Smart groups" tab: condition builder (`rule_json`) + "Re-evaluate" button (`/groups/{id}/evaluate`).

### 5.5. Compliance (Admin)

- "Compliance rate" card + policy list (`/compliance/policies`), overdue status, **Export PDF report** button (`/reports/export`).

---

## 6. Learner Group

### 6.1. Learner Portal

Goal: friendly, motivating, one primary action = "Continue learning".

```
┌── Hello, Minh ───────────────────────────────── [Report phishing] ──────┐
│  ┌─ Your progress ───────────────┐  ┌─ Badges ─────────────────────────┐ │
│  │  #####--  5/7 lessons  1,240   │  │ Guardian   Hunter                │ │
│  └───────────────────────────────┘  └──────────────────────────────────┘ │
│  ┌─ Lessons to complete ──────────────────────────────────────────────┐  │
│  │ > Spotting police impersonation     3 min   [Start now]            │  │
│  │ > Beware of deepfake voice cloning  4 min   [Start now]            │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│  ┌─ Department leaderboard ──────────┐  ┌─ Your certificates ──────────┐  │
│  │ 1. Lan   1,580 pts                │  │ Security Basics  [x]          │  │
│  │ 2. Minh  1,240 pts  <- you        │  │ Anti-phishing    In progress │  │
│  └───────────────────────────────────┘  └──────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

- **"Report phishing" button** is always prominent in the top-right corner (also available in the browser extension/email add-in) → `POST /reports/phishing`.
- **Sources:** `GET /enrollments`, `/users/{id}/badges`, `/users/{id}/points`, `/gamification/leaderboard`, `/users/{id}/certificates`.

### 6.2. Course Catalog & Learning Path

```
┌── Your courses ───────────────────────────  [All] [Required] [Suggested] ┐
│  Path: Office employee                       Overall progress: ####- 71%  │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐      │
│  │ Security     │ │ Phishing     │ │ Vishing      │ │ Deepfake     │      │
│  │   Basics     │ │              │ │              │ │              │      │
│  │ Completed    │ │ ###-- 3/5    │ │ Locked       │ │ Locked       │      │
│  │ [Review]     │ │ [Continue]   │ │ Opens after  │ │ Opens after  │      │
│  │              │ │              │ │ lesson 2     │ │ lesson 2     │      │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘      │
└──────────────────────────────────────────────────────────────────────────┘
```

- Course cards show status: completed / in progress (progress bar) / locked (with unlock conditions). Filter by *required* (compliance) vs *suggested* (adaptive).
- **Sources:** `GET /courses`, `GET /enrollments`. "Suggested" courses come from AI orchestration (`/ai/orchestration/run`).

### 6.3. Course Detail (lesson list)

```
┌── Course: Recognizing & preventing Phishing ──── 18 min · 5 lessons ───┐
│  Progress: ###-- 3/5    Required · Due 30/06    Level: Intermediate     │
│  1. What is phishing?                  Completed                        │
│  2. Signs of a spoofed email           Completed                       │
│  3. Fake brandname SMS                 In progress    [Continue]       │
│  4. Practice: spot real vs. fake email Not started                     │
│  5. Final quiz  (10 questions)         Opens after lessons 1–4         │
└────────────────────────────────────────────────────────────────────────┘
```

- Lessons are listed in order with sequential locking; the final quiz unlocks after all lessons are completed.
- **Sources:** `GET /courses/{id}` (lessons), `GET /enrollments/{id}` (progress).

### 6.4. Lesson Player

```
┌─ Lesson 3/5 · Fake brandname SMS ─────────────────── [Exit] ───┐
│ ######----  60%                                                 │
│ ┌── Content ──────────────────────────────┐ ┌─ In this lesson ─┐ │
│ │  [ video / illustration ]               │ │ x Concept        │ │
│ │                                         │ │ > How to spot it │ │
│ │  Fake brandname messages are inserted   │ │   How to handle  │ │
│ │  into a bank's real SMS thread...       │ │   Summary        │ │
│ │  Real example: [fake message image]     │ │                  │ │
│ └─────────────────────────────────────────┘ └──────────────────┘ │
│                              [Previous]       [Next]            │
└──────────────────────────────────────────────────────────────────┘
```

- Focused reading layout: main content on the left, lesson outline on the right; progress bar at the top; Previous/Next navigation.
- Supports diverse content: video, images, text, **interactive practice** (e.g., drag to highlight warning signs in a sample email).
- **Sources:** `GET /lessons/{id}`; record progress via `PATCH /enrollments/{id}`.

### 6.5. Take Quiz

A core screen that was missing in the previous version. One question at a time, with a timer, progress bar, and quick navigation between questions.

```
┌─ Final quiz: Phishing ───────────────── Question 4/10 · 06:21 ───┐
│ ####------                                                       │
│                                                                  │
│   Which is a SURE sign of a phishing email?                      │
│                                                                  │
│     ( )  A. The email includes a bank logo                       │
│     (x)  B. The sender domain is misspelled (bank-vn.support)    │
│     ( )  C. The email was sent in the morning                    │
│     ( )  D. The email has a signature at the bottom              │
│                                                                  │
│   [ ] Flag for later review                                      │
│                                                                  │
│              [Previous]                     [Next]              │
│   Navigation:  1 2 3 [4] 5 6 7 8 9 10        [Submit]            │
└──────────────────────────────────────────────────────────────────┘
```

- **Question types:** single answer (radio), multiple answers (checkbox), true/false, and **interactive** (inspect email: click the suspicious part; drag-and-drop to classify safe/phishing).
- **Mechanics:** countdown timer (optional per quiz), auto-save draft, "flag for review", and a "quick navigation" tracker showing answered/blank questions.
- **Before submitting:** a confirmation dialog if any question is left blank.
- **Sources:** `GET /lessons/{id}` (contains `quiz`); grading on submission via `enrollments` (updates `score`).

### 6.6. Quiz Results

```
┌─ Results ────────────────────────────────────────────────────────┐
│                    8/10  —  PASSED  (threshold 7/10)             │
│                 +120 points   ·   "Phishing Hunter" badge         │
│  ┌── Review answers ─────────────────────────────────────────┐    │
│  │ Q4    Correct                                             │    │
│  │ Q7    Incorrect  ·  Correct answer: B                     │    │
│  │       Why: a misspelled domain is the clearest sign;      │    │
│  │          a logo can easily be copied.                     │    │
│  └───────────────────────────────────────────────────────────┘    │
│            [View certificate]     [Continue to next lesson]      │
└──────────────────────────────────────────────────────────────────┘
```

- **Passed:** congratulations + points/badge + issue certificate button (if it's the final course quiz).
- **Not passed:** an encouraging (non-punitive) message + **"Review related lesson"** and **"Retry"** buttons; suggestions targeted at the weak knowledge areas.
- **Sources:** results from `enrollments`; certificate `GET /certificates/{id}`; badges `/users/{id}/badges`.

### 6.7. Placement Assessment & Survey

Two variants of "taking a quiz" beyond the course quiz:

- **Placement:** a few questions before entering the learning path so the AI assigns the right level → `POST /assessments/placement`.
- **Knowledge / security culture survey:** Likert-scale format, **can be anonymous** (culture surveys are not scored and not tied to identity).

```
┌─ Security culture survey (anonymous) ──────── Question 3/8 ──┐
│  "I know where to report phishing emails in my organization" │
│                                                              │
│   Strongly disagree ( ) ( ) ( ) ( ) ( ) Strongly agree       │
│                                                              │
│   Your answers are NOT linked to your identity               │
│                                         [Next]               │
└──────────────────────────────────────────────────────────────┘
```

- **Sources:** `GET /assessments`, `POST /assessments/{id}/responses` (`anonymous` flag).

### 6.8. Assigned Tasks (to-do)

```
┌─ To-do ──────────────────────────────────────────────────────┐
│  2 tasks due soon                                            │
│  [ ] Complete "Phishing quiz"             Due 30/06  [Do]     │
│  [ ] Watch "Beware of deepfake voice"     Due 02/07  [Learn]  │
│  [x] Security culture survey              Submitted          │
└──────────────────────────────────────────────────────────────┘
```

- Aggregates all assigned tasks (required lessons, quizzes, surveys) with due dates; syncs with reminders.
- **Sources:** `GET /enrollments?status=assigned,overdue`, `GET /notifications`.

### 6.9. Just-in-time Coaching Page (interstitial)

Appears the moment a user clicks a link in a **simulation** email.

```
┌───────────────────────────────────────────────────────────────────────┐
│                          This was a safety test                       │
│   You just clicked a link in a simulation email. Don't worry — no      │
│   harm done. Here are 3 signs you may have missed:                     │
│                                                                         │
│     1. Unfamiliar domain:  bank-vn.support  (not the official domain)  │
│     2. Manufactured urgency: "account locked within 24h"              │
│     3. Asks you to log in via a link instead of opening the app       │
│                                                                         │
│                 [ Got it ]          [ Take a 3-min lesson ]            │
└───────────────────────────────────────────────────────────────────────┘
```

- Positive tone, **non-punitive**; content sourced from `coaching_pages.signals_json`.

### 6.10. Certificates

- View & download PDF (`GET /certificates/{id}`), share internally; displays a serial number for verification.

---

## 7. SOC Analyst Group

### 7.1. Report Inbox (Inbox/Triage)

Goal: process reports quickly in bulk, prioritizing real threats.

```
┌── Report inbox ───────────────────────  Filter: [All v]  [Threat] ─────┐
│ [ ] Sender       Subject                     AI label    Confidence     │
│ [ ] Nguyen A     "Bank account locked"       Threat       0.96    2'    │
│ [x] Tran B       "iPhone prize win"          Spam         0.71    8'    │
│ [ ] Le C         "Weekly meeting schedule"   Clean        0.99    15'   │
│ ─────────────────────────────────────────────────────────────────────  │
│ 1 selected:  [Bulk quarantine]  [Mark clean]  [Watchlist]             │
└────────────────────────────────────────────────────────────────────────┘
```

- **AI label** column + **confidence** (color bar) help prioritization; bulk actions.
- **Sources:** `GET /reports/phishing?status=...`; actions `POST /reports/phishing/{id}/triage`.

### 7.2. Report Detail (Drawer)

```
┌── Report detail #4821 ─────────────────────────────────────── [x] ──┐
│ AI label: Threat (0.96)  ·  Reason: fake domain + login link         │
│ Blacklist check: MATCH (source: NCSC)                                │
│ ┌─ Email content (sanitized) ───────────────────────────────────┐    │
│ │ From: no-reply@bank-vn.support  ...                            │    │
│ └───────────────────────────────────────────────────────────────┘    │
│ Actions:                                                             │
│  [Confirm threat & broadcast alert]  [Flip into lesson (ThreatFlip)] │
│  [Add to blacklist]   [Close - clean]                               │
└─────────────────────────────────────────────────────────────────────┘
```

- **ThreatFlip:** the "Flip into lesson" button → `POST /reports/phishing/{id}/convert-to-training`.

### 7.3. Alert Center

- Compose an alert (severity: info/warning/critical) → **Broadcast org-wide** (`POST /alerts/broadcast`, pushed via WebSocket) + broadcast history.

### 7.4. Watchlist Management (suspicious accounts)

- `account-watchlist` table (type, value, risk_level, source SIMO/NAPAS/A05), add/sync (`/account-watchlist`), quick lookup box (`/account-watchlist/check`).

---

## 8. Realtime, Notifications & States

- **Realtime alert banner:** when an `alert.broadcast` arrives (WebSocket) → a red/amber strip at the top of the page for all roles; with "View details" and "Mark read" buttons (sends `ack`).
- **Notification bell:** unread counter; list `GET /notifications` (learning reminders, badges, alerts).
- **Toast:** action confirmation (alert broadcast, saved).
- **Mandatory states for every list/screen:**
  - *Loading:* skeleton.
  - *Empty:* illustration + 1 suggested action ("Create your first campaign").
  - *Error:* friendly message + "Retry" button.

---

## 9. Responsive & Accessibility

- **Breakpoints:** mobile `<640`, tablet `640–1024`, desktop `>1024`. The sidebar collapses into a sliding menu on mobile.
- **Mobile-first for Learners** (learn anywhere) and the **Report button**; Admin/Analyst optimized for desktop.
- **A11y:** WCAG AA contrast; clear focus ring; full keyboard operation; ARIA labels for charts; never use color *alone* to distinguish states (pair with icon/text label).
- **Simple content:** large-text mode & plain language for the education/community sector.

---

## 10. Screen ↔ API Map

| Screen | Role | Primary API |
|---|---|---|
| Admin dashboard | Admin/Manager | `/analytics/risk` `/analytics/benchmark` `/reports/phishing` |
| Create campaign (wizard) | Manager | `/groups` `/ai/templates/generate` `/sim/campaigns` |
| Campaign results | Manager | `/sim/campaigns/{id}` `/sim/events` |
| Users & groups | Admin | `/users` `/users/import` `/groups` `/groups/{id}/evaluate` |
| Compliance | Admin | `/compliance/policies` `/compliance/status` `/reports/export` |
| Learner portal | Learner | `/enrollments` `/users/{id}/badges` `/users/{id}/points` `/gamification/leaderboard` `/users/{id}/certificates` |
| Course catalog & path | Learner | `/courses` `/enrollments` `/ai/orchestration/run` |
| Course detail | Learner | `/courses/{id}` `/enrollments/{id}` |
| Lesson player | Learner | `/lessons/{id}` `/enrollments/{id}` |
| Take quiz | Learner | `/lessons/{id}` (quiz) `/enrollments/{id}` |
| Quiz results | Learner | `/enrollments/{id}` `/certificates/{id}` `/users/{id}/badges` |
| Placement assessment & survey | Learner | `/assessments` `/assessments/{id}/responses` `/assessments/placement` |
| Assigned tasks | Learner | `/enrollments?status=assigned,overdue` `/notifications` |
| Just-in-time coaching | Learner | `/coaching-pages` (+ sim tracking) |
| Certificates | Learner | `/certificates/{id}` |
| Report inbox | Analyst | `/reports/phishing` `/reports/phishing/{id}/triage` |
| Report detail + ThreatFlip | Analyst | `/reports/phishing/{id}/convert-to-training` `/blacklist` |
| Alert center | Analyst | `/alerts/broadcast` + WebSocket |
| Watchlist | Analyst | `/account-watchlist` `/account-watchlist/check` |
| Notifications (bell) | All roles | `/notifications` + WebSocket |

> The additional screens in **Section 11** have their API mappings noted within each subsection.

---

## 11. Full Screen Additions (Phase 2)

After review, the missing screens are added to fully cover all roles (including **Content Editor** and **Super Admin**) and end-to-end flows. Important screens include a wireframe; the rest are described concisely (purpose · components · API · states).

### 11.A — Authentication & Onboarding

#### 11.A.1 Login

```
┌──────────────────────────────────────────┐
│              DigiShield                     │
│   Sign in to continue                      │
│   ┌────────────────────────────────────┐  │
│   │ Work email                         │  │
│   └────────────────────────────────────┘  │
│   ┌────────────────────────────────────┐  │
│   │ Password                           │  │
│   └────────────────────────────────────┘  │
│   [          Sign in          ]           │
│   ──────────  or  ──────────              │
│   [ Sign in with SSO ]                    │
│   Forgot password?                         │
└──────────────────────────────────────────┘
```

- **API:** `POST /auth/login`; SSO button → `GET /auth/sso/login`.
- **States:** invalid credentials error, temporary lockout after N failed attempts, loading.

#### 11.A.2 SSO — Select Organization & Redirect to IdP

- Purpose: enter an organization code/name → an intermediate screen "Redirecting to Entra ID/Google…" → handle callback.
- Components: org input field, redirect spinner, assertion error handling ("Could not authenticate, please try again").
- **API:** `GET /auth/sso/login`, `POST /auth/sso/callback`.

#### 11.A.3 Forgot & Reset Password

- Step 1: enter email → "Link sent". Step 2: new password page (with strength requirements).
- **API (newly added):** `POST /auth/forgot-password`, `POST /auth/reset-password`.

#### 11.A.4 Two-Factor Authentication (MFA)

- Enter the OTP code from an authenticator app/SMS; option to "trust this device for 30 days". Required for admin roles.
- **API (newly added):** `POST /auth/mfa/setup`, `POST /auth/mfa/verify`, `POST /auth/mfa/challenge`.

#### 11.A.5 Invitation & First-time Onboarding

```
┌── Welcome to DigiShield ─────────────────────────────┐
│  You have been invited by [Organization ABC].        │
│  1. Set password   2. Choose language  3. Placement   │
│  ─────────────────────────────────────────────────── │
│  [ Set your password ... ]      Language: [English v] │
│              [ Get started ]                          │
└──────────────────────────────────────────────────────┘
```

- People who are imported/SCIM-provisioned (status `invited`) → set a password, choose a language, take the **placement assessment**, then enter the learning path.
- **API:** confirm invitation, `PATCH /users/{id}`, `POST /assessments/placement`.

### 11.B — Profile & Personal Settings

#### 11.B.1 Profile

- View/edit name, avatar, department; view personal risk score & badges. Accessed from the profile menu in the topbar.
- **API:** `GET /users/{id}`, `PATCH /users/{id}`.

#### 11.B.2 Personal Settings + Language Switch + a11y

- Change language (vi/en — `locale`), enable **large-text / plain-language mode**, change password, manage MFA.
- **API:** `PATCH /users/{id}` (`locale`), `/auth/mfa/*`.

#### 11.B.3 Notification Preferences

- Toggle receiving channels (in_app/email/sms) per type (learning reminders, alerts, badges).
- **API:** `GET/POST /notifications` (preferences), `Notification.channel`.

### 11.C — Notification Center (all roles)

```
┌── Notifications ─────────────────  [All][Reminders][Alerts] · Mark all read ──┐
│ [Alert] "SIM lock" phishing campaign is targeting the organization    2 min   │
│ [Reminder] "Phishing quiz" is due 30/06                               1 hr    │
│ [Badge] You earned the "Phishing Hunter" badge                        Yesterday│
└───────────────────────────────────────────────────────────────────────────────┘
```

- A full list (complementing the topbar bell), filterable by type, mark as read.
- **API:** `GET /notifications` + WebSocket `notification.new`.

### 11.D — Content Editor (Content Management)

#### 11.D.1 Content Studio (compose & review templates)

```
┌── Compose simulation template ──────────────── [Save draft][Submit for review] ┐
│ Channel: [Email v]   Difficulty: [Medium v]                                    │
│ ┌─ Editor ──────────────────────────┐ ┌─ AI assistant ──────────────┐          │
│ │ Subject: Tuition refund notice    │ │ Industry: [Education v]      │          │
│ │ Content: [rich text editor...]    │ │ Season:  [Start of year v]   │          │
│ │                                   │ │      [ Generate content ]    │          │
│ └───────────────────────────────────┘ └──────────────────────────────┘         │
│ AI moderation:  pass   (no content risk detected)                              │
└────────────────────────────────────────────────────────────────────────────────┘
```

- Template editor (email/SMS/Zalo…), **Generate with AI** button, displays **moderation** results (pass/flag/block), draft → approved workflow.
- **API:** `POST /ai/templates/generate`, `POST /ai/moderate`, template approval (PATCH).

#### 11.D.2 Simulation Template Library

- `sim_templates` management table: filter by channel/difficulty/status (draft/approved); serves as the source for the wizard.

#### 11.D.3 Manage Courses / Lessons / Quizzes

- Create/edit courses, order lessons by `order`, author quiz questions & `pass_score`, assign adaptive levels.
- **API:** `POST /courses`, `/lessons/{id}` (quiz).

#### 11.D.4 Manage Coaching Content

- Author just-in-time coaching pages tied to templates, define the list of "warning signs" (`signals_json`).
- **API:** `GET/POST /coaching-pages`.

### 11.E — Admin (extended)

#### 11.E.1 Create & Manage Surveys (Assessment Builder)

- Create question sets (knowledge/culture/placement), enable **anonymous**, set cadence, publish to an audience.
- **API:** `GET/POST /assessments`.

#### 11.E.2 Survey Results & Security Culture

- Aggregate charts by department (Likert/score), **anonymized** — cannot trace back to individuals.
- **API:** `GET /assessments/{id}/results`.

#### 11.E.3 Manage Notifications & Training Reminders

- Compose system notifications; schedule mandatory training reminders by `target_filter` + `due_rule` + channel.
- **API:** `POST /notifications`, `POST /notifications/reminders`.

#### 11.E.4 Manage Gamification

- Define badges (`criteria_json`), point rules, configure the leaderboard.

#### 11.E.5 Organization Settings (Org Settings)

- Tenant info, data residency (in-country/on-prem), risk score thresholds & business parameters.

#### 11.E.6 Import Job Monitoring

- Drawer of CSV/SCIM import results: number of rows accepted/failed, per-row error log (`job_id`).
- **API:** `POST /users/import`.

#### 11.E.7 AI Orchestration Console (AIDA)

- Trigger/schedule AIDA runs by scope, view auto-enroll history & affected people.
- **API:** `POST /ai/orchestration/run`.

### 11.F — SOC Analyst (extended)

#### 11.F.1 Blacklist Management

- `blacklist` table (url/phone/account, source NCSC), add manually, sync, filter by type/source.
- **API:** `GET/POST /blacklist`.

#### 11.F.2 Threat Intel & ThreatFlip Management

- Threat intel source table (NCSC/feed/user_report), conversion-to-template status, **convert** button + tracking of drafts awaiting approval.
- **API:** `GET/POST /threat-intel`, `POST /threat-intel/{id}/convert`.

#### 11.F.3 Transaction Intervention Log (Intervention Log)

- A table of every SDK risk-evaluation call; filter by decision (allow/warn/pause/block) & signal; serves auditing.
- **API:** `GET /interventions`.

### 11.G — Super Admin (new)

#### 11.G.1 Super Admin Console (tenant management)

```
┌── Tenant management ─────────────────────────────  [+ Create org] ──┐
│ Organization       Type       Users       Status       Data region  │
│ Agency ABC         gov        1,240       Active        In-country   │
│ XYZ University     edu        8,500       Active        In-country   │
│ DEF Company        enterprise 540         Suspended     Cloud        │
└──────────────────────────────────────────────────────────────────────┘
```

- Tenant list, create/suspend, configure global integrations, cross-tenant monitoring.

#### 11.G.2 SSO & SCIM Configuration

- Connect Entra ID/Google, configure SAML/OAuth, enable **SCIM**, map IdP attributes → role; monitor sync status.
- **API:** `/auth/sso/*`, `/scim/v2/Users`.

#### 11.G.3 Audit Log

```
┌── Audit log ──────────────────────  Filter: [User][Action][Time] ┐
│ Time              Performed by      Action                 Target       IP   │
│ 27/06 09:12       admin@abc         broadcast_alert        org ABC      ...  │
│ 27/06 08:55       analyst1          triage:confirm_threat  report#4821  ...  │
│ 27/06 08:40       admin@abc         user.role_change       user#88      ...  │
└──────────────────────────────────────────────────────────────────────────────┘
```

- Look up all sensitive actions (`audit_logs`), filter & export; serves investigation/compliance.

### 11.H — Learner (additions)

#### 11.H.1 Phishing Report Form/Modal (manual)

```
┌── Report phishing ─────────────────────────────── [x] ──┐
│ Type:  ( ) Email  (x) SMS  ( ) Phone call  ( ) Other     │
│ Content / paste the suspicious link:                     │
│ ┌────────────────────────────────────────────────┐      │
│ │                                                │      │
│ └────────────────────────────────────────────────┘      │
│ Attach a screenshot (optional)                          │
│                         [Cancel]  [Submit report]       │
└──────────────────────────────────────────────────────────┘
```

- **API:** `POST /reports/phishing` (`payload`, `channel`).

#### 11.H.2 My Learning History · 11.H.3 My Report History

- Completed courses + quiz scores (`GET /enrollments?status=completed`); track the status of submitted reports (`GET /reports/phishing` filtered to oneself).

#### 11.H.4 Full Badges Page · 11.H.5 Full Leaderboard

- All badges (earned/unearned + criteria), point history; the leaderboard supports period/scope filters.
- **API:** `/users/{id}/badges`, `/users/{id}/points`, `/gamification/leaderboard`.

#### 11.H.6 Praise Banner for a Correct Report

- When a user correctly reports a simulation email → a praise screen + points awarded (positive loop).
- **API:** `POST /sim/events` (action=report).

### 11.I — Transaction Intervention SDK (end-user warning screen)

```
┌──────────────────────────────────────────────────┐
│                 Wait — please check                │
│  You are transferring 50,000,000 VND to a NEW      │
│  account while on a phone call.                    │
│                                                    │
│  Is someone instructing you to transfer            │
│     money over the phone right now?                │
│                                                    │
│  - Police/banks will NEVER ask you to transfer     │
│    money to "prove your innocence".                │
│                                                    │
│   [ Pause & check ]        [ I'm sure ]            │
└──────────────────────────────────────────────────┘
```

- Displayed via the **embedded SDK** at the money-transfer confirmation step when `/interventions/evaluate` returns `warn/pause`. DigiShield only warns; the decision rests with the user & app.

### 11.J — Mobile, Public Pages, Errors & Help

- **11.J.1 Learner Mobile:** sidebar collapses into a sliding menu, a **floating "Report" button**, lesson/quiz scrolls vertically in a single column.
- **11.J.2 Public page (Landing):** product introduction, entry point to login/org sign-up (static).
- **11.J.3 Error & connection-loss pages:** global 404/403/500 + a "reconnecting realtime" state (WebSocket reconnect).
- **11.J.4 Help / Help Center:** FAQ, "where to report phishing", contact SOC.

---

*This UI/UX specification supports design & development. Wireframes are at the layout (low-fi) level — pixel/color details are finalized in the hi-fi design phase (Figma).*
