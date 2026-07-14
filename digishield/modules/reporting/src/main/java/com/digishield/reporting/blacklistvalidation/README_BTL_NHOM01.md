# BTL Group 01 - Blacklist normalization & validation (module `reporting`)

Reference scaffold for topic #01. Other groups clone this structure for their own module.

## Feature
New endpoint `POST /api/v1/blacklist/validate` - takes `{type, value}`, returns
`{valid, normalized, reason}`. Does not touch the existing `POST /api/v1/blacklist`, so it
does not collide with other groups.

## Files in the scaffold
| File | Role |
|------|------|
| `BlacklistValueValidator.java` | **Pure logic** validate + normalize per `BlacklistType`. Where coverage must exceed 90%. |
| `ValidationResult.java` | Result record (`valid`, `normalized`, `reason`). |
| `BlacklistValidationController.java` | New REST endpoint, dedicated path `/validate`. |
| `../../../test/.../BlacklistValueValidatorTest.java` | Data-driven unit tests (passing, high branch coverage). |
| `../../../test/.../BlacklistValidationControllerTest.java` | Controller test (status + body). |
| `modules/reporting/build.gradle.kts` | JaCoCo gate `minimum = 0.90` scoped to the feature package. |

## What the group must do (TODO)
1. Add the missing rules (IPv6, phone length boundaries 8..15, SHA-1/256 hashes) - **write tests with the code**.
2. (Optional) Upgrade the controller test to `@WebMvcTest` - add
   `testImplementation("org.springframework.boot:spring-boot-starter-web")` to `build.gradle.kts`.
3. Wire the validator into `ReportingServiceImpl.addBlacklist(...)` to **store the normalized value** and **deduplicate** per tenant.
4. Write a Postman collection for `/validate` (API tests - session 9) plus a Selenium scenario if there is UI (sessions 10-11).
5. Fill in the per-case specification table (session-9 template) in the report.

## Run & verify
```bash
# test + 0.90 coverage gate + checkstyle for this module only
./gradlew :modules:reporting:check

# HTML report
digishield/modules/reporting/build/reports/jacoco/test/html/index.html
```

## Submission
1. Branch: `btl/nhom01-blacklist-validation` (already created).
2. Commit with **Conventional Commits** (enforced by `commit-lint`), e.g. `feat(reporting): add blacklist value validation endpoint`.
3. Open a PR into `develop` -> wait for **Backend CI** to be green -> merge.
