# Handoff: DigiShield — Lá Chắn Số

## Overview
DigiShield (Lá Chắn Số) is a Digital Security Awareness Platform that simulates phishing attacks (email, SMS, QR, voice) to train employees, automatically assigns remediation training, and tracks organizational risk. This handoff covers the complete UI for 4 user roles across 45+ screens.

## About the Design Files
The files in this bundle (`DigiShield.dc.html`) are **high-fidelity design references created in HTML** — fully interactive prototypes showing intended look, behavior, and data flow. They are **not production code to ship directly**.

Your task is to **recreate these designs in your target codebase** (React, Vue, Next.js, etc.) using its established patterns, routing, and libraries. If no environment exists yet, **React + Next.js** with Tailwind CSS is recommended.

---

## Fidelity
**High-fidelity.** Pixel-perfect mockups with:
- Final colors, typography, spacing, interactions
- Real component structure and data flow
- All 4 role-based navigation trees
- Working quiz, wizard, and drawer interactions

The developer should recreate the UI **pixel-perfectly** using the codebase's existing design system.

---

## Design Tokens

### Colors
```
--navy:      #12284B   Sidebar, heavy headings
--blue:      #2566EB   Primary action, links, active states
--teal:      #18935C   Safe/success states
--red:       #DD3B40   Threats, danger actions
--amber:     #E08A0B   Warnings, medium risk
--bg:        #F5F8FC   App background
--bg-auth:   #F0F4FF   Auth page backgrounds
--surface:   #FFFFFF   Cards, panels
--border:    #DCE3EE   Card borders, separators
--input-bg:  #F5F8FC   Input/select backgrounds
--input-bdr: #C0CBDC   Input borders
--text:      #141A26   Primary text
--muted:     #69788F   Secondary text, labels

Risk scale:
  Low:    #18935C (score 0–39)
  Medium: #E08A0B (score 40–69)
  High:   #DD3B40 (score 70–100)
```

### Typography
```
Display / Headings:  Space Grotesk (700, tracking -0.02em)
Body / UI:           Hanken Grotesk (400/500/600)
Data / Code / IDs:   JetBrains Mono (400/500/600)
```

### Spacing scale (px)
`4 · 8 · 12 · 16 · 20 · 24 · 28 · 32 · 48`

### Border radius
```
Cards/panels:  12px
Buttons/inputs: 8–9px
Pills/tags:    999px (full round)
Large panels:  16px
```

### Shadows
```
Card:   0 2px 8px rgba(0,0,0,.06)
Modal:  0 8px 32px rgba(0,0,0,.12)
Auth:   0 4px 24px rgba(37,102,235,.08)
```

---

## Screens & Views

### Auth Flows

#### Login (`/login`)
- Centered card (400px wide) on `#F0F4FF` background
- Logo: Shield SVG + "DigiShield" in Space Grotesk 700
- Role selector: 4-pill segmented control (Admin / Learner / Analyst / Super)
- Email + password inputs
- Primary CTA: "Đăng nhập" full-width blue button
- Secondary: SSO button (outline)
- Footer: version string

#### Forgot Password (`/auth/forgot-password`)
- Same card layout, 380px wide
- Email input → "Gửi link đặt lại mật khẩu" button
- Success state: green confirmation banner inline

#### MFA (`/auth/mfa`)
- 6-box OTP input (48×56px each, JetBrains Mono 22px)
- "Tin thiết bị này 30 ngày" checkbox
- "Xác nhận" blue CTA

#### SSO (`/auth/sso`)
- Org domain input + "Tiếp tục" button
- Spinner "Đang chuyển hướng tới Microsoft Entra ID..."
- 3 IdP pills: Microsoft Entra / Google Workspace / SAML 2.0

#### Onboarding (`/onboarding`)
- 3-step progress dots
- Step 1: Set password + language selector
- Password strength bar (4 segments, color-coded)
- CTA leads to placement assessment

---

### App Shell
```
Layout: flex row, full viewport height
├── Sidebar: 240px wide, fixed
│   ├── Logo header: 56px
│   ├── Role switcher: 4 pills
│   ├── Nav items: icon (15px Lucide) + label, 9px 10px padding, 8px radius
│   └── Profile card: avatar + name + subtitle
└── Main: flex-1
    ├── Topbar: 56px, page title + search + notification bell + avatar
    └── Content area: flex-1, overflow-y:auto, 24px 28px padding
```

**Navigation active state:** `rgba(37,102,235,.08)` bg + `#1A4FD0` text

---

### Admin / Manager Screens

#### Dashboard (`/dashboard`)
4 KPI tiles in a 4-col grid:
1. **Risk Score** — semicircle gauge SVG (0–100), color: green/amber/red by range
2. **Phish-prone %** — large number + trend badge + industry benchmark
3. **Hoàn thành ĐT** — percentage + progress bar
4. **Cảnh báo mở** — count in red + critical/warning breakdown

Below: 2-col row
- Line chart: 90-day risk trend (SVG polyline, blue stroke 2.5px, gradient fill)
- Benchmark bar chart: 3 horizontal bars (org vs industry avg)

Bottom 2-col:
- Department risk bars (horizontal, color-coded by score)
- Recent reports list (dot indicator + title + AI label pill)

#### Campaign Wizard (`/campaigns/new`) — 5-step stepper
```
Step indicator: circles (30px) connected by lines
Active: #2566EB fill · Completed: #18935C fill + ✓ · Inactive: #DCE3EE
```

**Step 1 — Channel:** 2×2 card grid (Email / SMS / QR / Zalo), selection state = blue border + tint bg

**Step 2 — Template:** 2-col layout
- Left: template list (selectable rows with difficulty dots)
- Right: email preview (monospace sender, content preview)

**Step 3 — Audience:** radio-style rows, smart group badge

**Step 4 — Schedule:** date/time pickers + 3 send-speed radio options

**Step 5 — Preview & Launch:** 2×2 summary grid + large "Phát động" red CTA

#### Campaign Results (`/campaigns/:id`)
- Status badge pill (Completed = green)
- Funnel chart: 5 rows (Gửi→Mở→Bấm→Nhập→Báo cáo), bar widths proportional, colored
- Results table: 4 cols (Name / Department / Action / Learning status)

#### Users & Groups (`/users`)
- Filter bar: search + role dropdown + department dropdown
- Table: Name+email / Role / Department / Risk pill / Edit link
- Import CSV button

#### Compliance (`/compliance`)
- 3 KPI tiles: completion rate / completed policies / due soon
- Policy list: name + deadline + progress bar + % pill

#### Content Studio (`/content/studio`)
- 2-col layout: template library sidebar + editor main
- Library: filter pills (Tất cả/Email/SMS) + template cards with status badges
- Editor: title input + textarea + Save Draft / Submit buttons
- AI moderation result: green pass banner

#### Org Settings, Gamification, AIDA — see prototype for details

---

### Learner Screens

#### Portal (`/learn`)
- Greeting + "Báo cáo lừa đảo" red CTA (always visible top-right)
- 2-col: progress card (bar + badge chips) + points/leaderboard card
- Tasks list: grouped by urgency (overdue → upcoming → done)

#### Course Catalog (`/learn/courses`)
- 4-col card grid: icon bg + course title + progress bar + CTA button
- States: Completed (green) / In progress (blue border) / Locked (dimmed)

#### Lesson Player (`/learn/lessons/:id`)
- Full-width progress bar (top)
- 2-col: main content (video/text area) + sidebar outline (4 checkpoints)
- Prev/Next navigation

#### Quiz (`/learn/quiz/:id`)
- Question number + countdown timer pill (red)
- Progress bar
- Question text (Space Grotesk 18px 600)
- 4 answer options: labeled A/B/C/D (28px circle) + text rows
  - Selected: `rgba(37,102,235,.06)` bg + blue border 2px
- Bottom: prev/next nav + numbered dot nav + Submit button

#### Quiz Results (`/learn/quiz/:id/results`)
- Score circle (80px, green or red tint)
- Points earned + total points
- Answer review accordion: ✓/✗ icon + explanation for wrong answers
- CTAs: View Certificate + Next Lesson

#### Certificate (`/certificates/:id`)
- Bordered card (blue 2px border + top gradient stripe)
- Shield icon + course title + recipient + 3 metadata tiles
- QR code SVG + serial number
- Download PDF + Share buttons

---

### SOC Analyst Screens

#### SOC Inbox (`/soc/inbox`)
- Filter tabs: Tất cả / THREAT / SPAM / CLEAN with counts
- Table: grid `32px 1fr 110px 80px 44px`
  - Checkbox (18px square) / Report info / AI label pill / Confidence % + bar / Time ago
- Bulk action bar (appears when rows selected): Confirm Threat / Mark Clean / Isolate

#### Report Drawer (slide-in panel, 480px from right)
- Header: report ID + sender name
- AI judgment section: label + confidence + reasoning
- Blacklist match indicator (amber warning)
- Sanitized email preview (monospace, dark bg)
- 4 action buttons: Confirm Threat (red) / ThreatFlip (blue ghost) / Blacklist / Close-Clean

#### Alert Center (`/soc/alerts`)
- Compose form: severity select + textarea + broadcast button (red)
- History list: past broadcasts with severity badge + reach count

#### Watchlist / Blacklist / Threat Intel / Intervention Log — see prototype

---

### Super Admin Screens

#### Tenant Console (`/super/tenants`)
- 3 KPI cards: tenant count / total users / in-country count
- Table: org name+domain / type / user count / status pill / data region / Manage link

#### SCIM & SSO Config (`/super/scim`)
- Connected IdP card (green "Đã kết nối" badge)
- Tenant ID + Client ID display fields
- SCIM endpoint URL + copy button
- Sync status bar
- Attribute mapping grid

#### Audit Log (`/super/audit`)
- Filter bar: Actor / Action / Date
- Table: `130px 1fr 180px 140px 100px` (Time / Actor / Action badge / Object / IP)
- Action badges: color-coded (red=critical, amber=sensitive, gray=standard)

---

## Interactions & Behavior

### Navigation
- Role switcher (sidebar pills) → immediately changes visible nav items and navigates to role home
- All nav items: instant client-side navigation, no page reload
- Active item: blue tint bg + blue text

### Campaign Wizard
- Step validation: Next button disabled until required selection made
- Step 5 Launch: shows confirmation, then toast + navigate to results

### Quiz
- Answer selection: immediate visual feedback (blue border)
- Dot navigation: green = answered, blue = current, gray = unanswered
- Submit: validates all answered, shows results screen

### SOC Triage
- Row click → opens right drawer (480px slide-in, backdrop overlay)
- Checkbox click (stops propagation) → bulk selection mode

### Realtime / Toast
- Alert banner: fixed top, full-width red/amber, dismissible
- Toast: bottom-right, auto-dismiss 4.5s, slide-in animation

### Transitions
```css
All navigations: fadeUp 0.3s ease (opacity 0→1, translateY 8px→0)
Drawer: slideInRight 0.25s ease
Toast: toastIn 0.25s ease (opacity + translateX)
Duration base: 150–250ms
Easing: ease (default), no bounce
```

---

## State Management

### Key state variables
```js
screen: string          // current screen name
role: 'admin' | 'learner' | 'analyst' | 'super'
wizardStep: 1–5         // campaign wizard step
wizardChannel/Template/Group: selection state
quizQ: number           // current question index
quizAnswers: {[q]: answer}
drawerOpen: boolean
drawerReportId: number | null
triageSelected: number[]
surveyAnswer: number | null
csTmpl: 1|2|3           // content studio selected template
wlFilter: string        // watchlist filter
showBanner: boolean     // alert banner
notifOpen: boolean      // notification dropdown
toasts: [{id, msg}]
```

### API Endpoints (from spec)
See `DigiShield_openapi.yaml` for full spec. Key endpoints:
- `POST /auth/login` · `GET /auth/sso/login`
- `GET /analytics/risk` · `GET /analytics/benchmark`
- `POST /sim/campaigns` · `GET /sim/campaigns/:id`
- `GET /enrollments` · `PATCH /enrollments/:id`
- `GET /lessons/:id` · `POST /assessments/:id/responses`
- `GET /reports/phishing` · `POST /reports/phishing/:id/triage`
- `POST /alerts/broadcast` (+ WebSocket `alert.broadcast`)
- `GET /notifications` (+ WebSocket `notification.new`)

---

## Assets
- **Logo**: Shield SVG (inline, path data in HTML file) + "DigiShield" wordmark
- **Icons**: Lucide icon set (SVG, 15×15px, stroke 2px, strokeLinecap round)
- **Fonts**: Google Fonts — Space Grotesk, Hanken Grotesk, JetBrains Mono

---

## Files in this package
```
design_handoff_digishield/
├── README.md              ← This file
└── DigiShield.dc.html     ← Full interactive design reference (45+ screens)
```

Open `DigiShield.dc.html` directly in a browser to view the complete prototype.

---

## Implementation Notes

1. **Role-based routing**: The sidebar nav items are fully RBAC-filtered — implement as route guards or conditional nav components per role.

2. **Light theme throughout**: All surfaces use light palette. No dark mode in v1.

3. **Vietnamese text**: UI is Vietnamese-first. All labels, CTAs, and content are in Vietnamese with English subtitles on headings.

4. **No emoji in production UI**: The prototype uses emoji as placeholder icons. Replace with Lucide SVG icons in production.

5. **Realtime**: Alert banner and notification bell use WebSocket. Mock with SSE or polling in development.

6. **Quiz interactivity**: The quiz is fully functional in the prototype — replicate the selection state, dot navigation, and submit validation exactly.
