# DigiShield

Skeleton of a **modular monolith** built on **Spring Boot 4.1.0** + **Spring Modulith 2.1.0**,
managed with **Gradle Kotlin DSL** (multi-module, Gradle **9.6.1** wrapper), Java toolchain **25**.

- `group = com.digishield`, `version = 0.1.0-SNAPSHOT`
- Base package: `com.digishield`
  - module: `com.digishield.<module>`
  - shared: `com.digishield.shared.<name>`
  - contracts: `com.digishield.contracts`

---

## Directory structure

```
digishield/
├── settings.gradle.kts          # declares all subprojects
├── build.gradle.kts             # common config + aggregated JaCoCo report
├── gradle.properties            # enables caching + parallel
├── gradlew / gradle/wrapper/    # Gradle 9.6.1 wrapper (committed)
├── buildSrc/                    # shared convention plugins
│   └── src/main/kotlin/
│       ├── digishield.spring-module-conventions.gradle.kts
│       └── digishield.spring-boot-app-conventions.gradle.kts
├── boot/
│   └── app/                     # Spring Boot bootstrap (a single boot only)
│       ├── build.gradle.kts
│       └── src/main/java/com/digishield/
│           ├── DigishieldApplication.java
│           └── AppRunModes.java  # api / worker / scheduler by profile
├── contracts/                   # API/event contracts (DTOs + events)
├── shared/                      # shared libraries
│   ├── persistence/  tenant-context/  messaging/  security/  observability/
├── modules/                     # 9 business modules
│   ├── auth/ tenancy/ learning/ simulation/ reporting/
│   └── analytics/ notification/ ai/ interception/
├── db/
│   ├── migration/               # Flyway migrations (V2026.06.27.001 init, .002 fe_alignment)
│   └── SCHEMA_AUDIT.md          # JPA entities vs V001 audit (basis for .002)
├── deploy/
│   ├── docker/Dockerfile        # multi-stage temurin 25, layered jar
│   ├── compose/docker-compose.yml          # full api/worker/scheduler + infra
│   ├── compose/docker-compose.prodlike.yml # single api on real Postgres+Flyway (see RUN_PRODLIKE.md)
│   └── helm/digishield/         # Chart + values + templates
└── RUN_PRODLIKE.md              # run the backend prod-like (Postgres + Flyway)
```

> The **React frontend** lives in a sibling repository (`../frontend`), not inside this
> Gradle build — it is a self-contained Vite/TypeScript toolchain. Product/architecture
> docs (including `DigiShield_openapi.yaml`) live in the sibling `../docs` folder.

### Convention plugins

| Plugin id | Applies to | Description |
|-----------|-------------|-------|
| `digishield.spring-module-conventions` | `shared:*`, `modules:*`, `contracts` | `java-library` + dependency-management + Spring Boot/Modulith BOM + default starters |
| `digishield.spring-boot-app-conventions` | `boot:app` | `org.springframework.boot` plugin + `bootJar` (layered) |

### Run modes (same artifact, different profile)

| Profile | Role |
|---------|---------|
| `api` | serves HTTP (controllers + actuator) |
| `worker` | processes background events/messages |
| `scheduler` | runs periodic jobs (`@EnableScheduling`) |
| `flyway` | runs migrations (separate job) |

---

## Build

The Gradle **9.6.1** wrapper is committed, so no local Gradle install is needed:

```bash
# Build everything
./gradlew build

# Verify the Modulith structure (boundaries + documentation generation)
./gradlew :boot:app:test
```

> Requires **JDK 25** (the Gradle toolchain will download it automatically if toolchain provisioning is configured).

---

## Run the backend for the frontend (dev)

A dedicated **`dev`** profile boots the backend with an in-memory database and
permissive security so the React frontend (Vite dev server at
`http://localhost:5173`) can call it without any external infrastructure.

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

This serves the API at **`http://localhost:8080/api/v1`**.

What the `dev` profile turns on (everything else — prod — is unchanged):

| Concern | dev | default / prod |
|---------|-----|----------------|
| Database | **H2** in-memory (`jdbc:h2:mem:digishield`, PostgreSQL mode), schema built from JPA entities (`ddl-auto=create-drop`) | PostgreSQL |
| Migrations | Flyway **disabled** | Flyway (separate job) |
| Security | `permitAll()`, CSRF off (`DevSecurityConfig`) | JWT resource server (`SecurityConfig`, `@Profile("!dev")`) |
| Tenant | `DevTenantFilter` sets `X-Tenant-Id` or the fixed demo tenant `11111111-1111-1111-1111-111111111111` | `TenantFilter` (JWT `tid` / header) |
| RLS | `RlsTenantAspect` **disabled** (`@Profile("!dev")`, H2 has no `set_config`) | Postgres Row-Level Security |
| CORS | origin `http://localhost:5173`, methods `GET/POST/PUT/PATCH/DELETE/OPTIONS`, all headers, `Authorization` + `X-Tenant-Id` exposed, credentials on (`CorsConfig`) | configurable via `digishield.cors.allowed-origins` |
| Seed data | one demo `AppUser` per role (`super_admin`, `org_admin`, `manager`, `content_editor`, `analyst`, `learner`) under the demo tenant (`DevDataSeeder`); other modules add their own `@Profile("dev")` seeders | none |

Optional H2 web console: `http://localhost:8080/h2-console`
(JDBC URL `jdbc:h2:mem:digishield`, user `sa`, empty password).

### Dev auth & users endpoints

- `POST /api/v1/auth/login` — body `{ "email", "password", "role"? }` → `TokenPair`
  `{ access_token, refresh_token, expires_in }` (dev returns static tokens; the
  optional `role` picks the demo persona).
- `POST /api/v1/auth/refresh` — body `{ "refresh_token" }` → `TokenPair`.
- `GET /api/v1/auth/me` — current user `{ id, tenantId, email, role, name }`.
  Send `X-Demo-Role: <role>` (e.g. `analyst`) to switch persona.
- `GET /api/v1/users` — users for the Users screen
  (`id, org_id, email, name, role, status, department, riskScore / risk_score`).

### Frontend `.env`

The frontend lives in the sibling `../frontend` repo. Point it at the dev backend via
its `.env`:

```dotenv
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

---

## Testing & Quality

### Testing strategy: unit tests (UT) vs integration tests (IT)

Tests are split by **naming convention** and **source-set location** so that the fast,
Docker-free suite stays separate from the heavyweight Spring/Testcontainers suite.

| Type | Naming | Location | What it is | Needs Docker? | Runs in task |
|------|--------|----------|------------|---------------|--------------|
| **Unit (UT)** | `*Test.java` | `modules/<m>/src/test/java/...`, `boot/app/src/test/java/...` | Pure Mockito tests, no Spring context. Includes `ModularityTests` (Modulith boundary verification — fast, no Docker). | No | `test` |
| **Integration (IT)** | `*IT.java` | `boot/app/src/integrationTest/java/com/digishield/it/` | Spring context / Testcontainers slices: `TenantIsolationIT`, `SimulationModuleIT`, `InterceptionControllerIT` (@WebMvcTest slice). RLS bootstrap SQL lives at `boot/app/src/integrationTest/resources/it/rls-setup.sql`. | Yes | `integrationTest` |

The IT suite is implemented as a Gradle **JVM Test Suite** (`integrationTest`) on
`boot/app` only — it has its own source set, classpath and task, and is configured to
run *after* the unit `test` task. `check` depends on both.

### Run tests

```bash
# Unit tests ONLY — fast, no Docker required
./gradlew test

# Integration tests ONLY — requires a running Docker daemon (Testcontainers)
./gradlew integrationTest

# Everything: unit + integration tests + Checkstyle + JaCoCo verification
./gradlew check
```

> Integration tests (`TenantIsolationIT`, `SimulationModuleIT`) use **Testcontainers**
> (PostgreSQL), so a running **Docker** daemon is required. The Testcontainers BOM
> (`1.20.4`) is wired into the `integrationTest` suite in `boot/app/build.gradle.kts`
> together with `spring-boot-testcontainers`, `testcontainers:junit-jupiter` and
> `testcontainers:postgresql`.

### Coverage (JaCoCo)

The `jacoco` plugin is applied by both convention plugins. Running `test` finalises with
`jacocoTestReport`; `check` also runs `jacocoTestCoverageVerification`
(bundle **LINE** coverage minimum **0.10** — a lenient skeleton threshold, see the
`// TODO raise threshold as coverage grows` comment). Exclusions cover generated /
boilerplate code (`**/*Application*`, `**/config/**`, `**/*Config*`, `**/package-info*`);
the service `application/**` classes stay in scope.

```bash
# Per-module coverage report (after running tests)
./gradlew test jacocoTestReport
#  -> <module>/build/reports/jacoco/test/html/index.html

# Single aggregated report across ALL subprojects
./gradlew testCodeCoverageReport
#  -> build/reports/jacoco/testCodeCoverageReport/html/index.html
#  -> build/reports/jacoco/testCodeCoverageReport/testCodeCoverageReport.xml
```

### Code style (Checkstyle)

Checkstyle (`toolVersion 10.21.0`) is applied by both convention plugins. The shared
config lives in `config/checkstyle/checkstyle.xml` with suppressions in
`config/checkstyle/suppressions.xml` (skips `package-info.java` and `build/` output).

```bash
# Run Checkstyle on production + test sources
./gradlew checkstyleMain checkstyleTest

# Checkstyle also runs automatically as part of:
./gradlew check
```

- `maxWarnings = 0` — any violation is reported.
- `isIgnoreFailures = true` for now so the build stays green while skeleton code is
  cleaned up. Flip to `false` (see the `// TODO flip to false to enforce` comment in
  the convention plugins) to make violations fail the build.
- Editor settings are pinned via `.editorconfig` (UTF-8, LF, 4-space Java indent).

---

## Run with Docker Compose

```bash
# Build the image + bring up api/worker/scheduler + postgres/redis/rabbitmq
docker compose -f deploy/compose/docker-compose.yml up --build
```

- API: http://localhost:8080 (actuator: `/actuator/health`)
- Postgres: `localhost:5432` (db/user/pass = `digishield`)
- RabbitMQ UI: http://localhost:15672
- Redis: `localhost:6379`

### Prod-like (real Postgres + Flyway)

To exercise the actual migration path against a real PostgreSQL — while keeping the
permissive dev security and demo seeders so the frontend works end-to-end — use the
**prod-like** compose stack (single `api` on `dev,pgdemo` profiles). See
[`RUN_PRODLIKE.md`](RUN_PRODLIKE.md) for details:

```bash
docker compose -f deploy/compose/docker-compose.prodlike.yml up --build
```

---

## Helm deployment

```bash
# Cloud / default
helm install digishield deploy/helm/digishield

# On-premises
helm install digishield deploy/helm/digishield -f deploy/helm/digishield/values-onprem.yaml
```

The `job-flyway.yaml` job runs as a Helm hook (`pre-install`/`pre-upgrade`) to apply migrations
before rolling out the Deployments.

---

## CI

> CI lives at the **monorepo root** (`../.github/workflows/`, i.e. the
> `DigiShield_Project/` git root — GitHub only reads workflows from the repo root),
> with **path filters** so each side runs only when its code changes.

**`backend-ci.yml`** (runs on `digishield/**`), all Gradle steps in `working-directory: digishield`:
1. `setup-java` 25 + Gradle, then:
   - `./gradlew test` — fast unit-test feedback (no Docker).
   - `./gradlew check jacocoTestReport testCodeCoverageReport` — integration tests
     (Testcontainers; ubuntu-latest runners ship Docker), **Checkstyle**, **JaCoCo**
     verification, per-module reports and the aggregated coverage report.
   - Checkstyle and test reports are uploaded on failure; JaCoCo per-module and
     aggregated coverage reports are uploaded always.
2. Build the Docker image (`context: digishield`, `file: digishield/deploy/docker/Dockerfile`).

**`frontend-ci.yml`** (runs on `frontend/**` or `docs/DigiShield_openapi.yaml`), in
`working-directory: frontend`: `npm ci` → `npm run gen:api` (generate the typed client
from the OpenAPI spec) → `lint` → `typecheck` → `test` → `build`, uploading `dist/`.
