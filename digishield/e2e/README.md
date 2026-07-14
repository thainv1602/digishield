# Automation tests - E2E (Selenium) + API (Newman)

Automation structure for DigiShield, aligned with the course slides (sessions 9-12). Two layers:

| Layer | Tool | Location | Run |
|-------|------|----------|-----|
| **E2E UI** | Selenium + JUnit 5 (Page Object) | `e2e/` (module `:e2e`) | `./gradlew :e2e:test -De2e.enabled=true` |
| **API** | Newman (Postman) | `postman/` | `cd postman && npm install && npm run test:api` |

## `:e2e` module layout
```
e2e/
  build.gradle.kts                      # Selenium + JUnit; test guard -De2e.enabled
  src/test/java/com/digishield/e2e/
    pages/        LoginPage, SocInboxPage, WatchlistPage   # Page Object (POM)
    support/      DriverFactory, ApiHelper, ScreenshotOnFailure
    scenarios/    AnalystBlocksAccountE2E                  # sample E2E scenario
```

## Why the E2E test is "guarded"
The scenario class is annotated `@EnabledIfSystemProperty(named = "e2e.enabled", matches = "true")`.
- **Unit CI** (`backend-ci.yml`, no running app) -> the E2E test is **skipped**, so the pipeline stays green.
- **automation-ci** (backend :8080 + frontend :5173 already up) -> runs with `-De2e.enabled=true`.

## Run locally
```bash
# Terminal 1 - backend dev (H2 seed)
cd digishield && ./gradlew :boot:app:bootRun --args='--spring.profiles.active=dev'
# Terminal 2 - frontend
cd frontend && npm run dev            # :5173
# Terminal 3 - E2E (open a Chrome window to watch: disable headless)
cd digishield && ./gradlew :e2e:test -De2e.enabled=true -Dselenium.headless=false
# API tests
cd digishield/postman && npm install && npm run test:api
```
Failure screenshots: `e2e/build/screenshots/`. API HTML report: `postman/reports/api-report.html`.

## Note on authorization negatives (401/403)
The dev profile is intentionally permissive (`SecurityConfig` / `MethodSecurityConfig` are
`@Profile("!dev")`, so `@PreAuthorize` is inert). The Newman smoke suite therefore uses
input-level negatives (400/404). Role/tenant enforcement (401/403, RLS) is covered by the
integration tests (`TenantIsolationIT`, `RlsCoverageIT`) which run under a secured profile.

## Status of the sample scenario
`AnalystBlocksAccountE2E` is a **reference template**. In `automation-ci` its E2E step is
`continue-on-error: true` (runs, uploads a failure screenshot, but does not fail the pipeline)
because it must be hardened against the live UI first. Once a group has a verified-green
scenario, remove `continue-on-error` to make E2E a real gate.

## How BTL groups extend this
- Add a Page Object for the new screen under `pages/`, a new scenario under `scenarios/`.
- Add a request for the group's feature to `postman/digishield.postman_collection.json`
  (with at least one negative case).
