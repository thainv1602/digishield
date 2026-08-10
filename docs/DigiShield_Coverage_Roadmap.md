# DigiShield — Coverage roadmap to 0.90

> Version 1.0 · 10/08/2026
> Expands `DigiShield_FRD.md` §8.1, which sets the policy. This file sets the schedule.

The target is **0.90 line coverage, measured per subproject**. Per subproject rather than
aggregate, because an average lets a well-tested module hide an untested one.

The floor rises in rungs, and **a rung is only raised once every subproject already clears
it**. The gate therefore never sits above reality, and no group ever inherits a red build
it did not cause.

---

## 1. Where we are

Measured on 10/08/2026 from `jacocoTestReport`, unit suite:

| Subproject | Covered / total | % |
|---|---|---|
| `modules/reporting` | 64 / 323 | **19.8%** |
| `modules/tenancy` | 157 / 743 | **21.1%** |
| `boot/app` | 162 / 640 | **25.3%** |
| `modules/notification` | 140 / 338 | 41.4% |
| `modules/learning` | 558 / 1207 | 46.2% |
| `modules/ai` | 244 / 515 | 47.4% |
| `modules/simulation` | 169 / 336 | 50.3% |
| `modules/auth` | 283 / 537 | 52.7% |
| `shared/security` | 25 / 47 | 53.2% |
| `modules/interception` | 103 / 184 | 56.0% |
| `shared/tenant-context` | 69 / 114 | 60.5% |
| `modules/analytics` | 231 / 311 | **74.3%** |
| **Total** | **2205 / 5295** | **41.6%** |

The current floor is `0.15`. The weakest subproject is at 19.8%, so the gate is presently
about 4 points below what is already true — it protects against regression and nothing more.

## 2. Fix the measurement before chasing the number

**`boot/app` at 25.3% is not its real coverage.** Its integration tests — `TenantIsolationIT`,
`SimulationModuleIT` and the rest — are not counted: `testCodeCoverageReport` produces the
same figure whether or not the Testcontainers suite has run, because the `integrationTest`
exec data is never wired into the aggregation.

Nobody should be asked to raise a number that is measured wrongly. Wiring the
`integrationTest` exec data into the report is **rung zero**, and it will move `boot/app`
upward for free.

Two smaller measurement rules, so the number keeps meaning something:

- The exclusion list stays closed — generated sources, framework configuration,
  `package-info`. Nothing is excluded for being inconvenient to test.
- Coverage measures execution, not assertion. `./gradlew check` also runs **SpotBugs** and
  **Checkstyle**, and topic ĐT15 adds **PITest**: mutation score is what distinguishes a
  test that checks something from a test that merely runs it.

## 3. The rungs

Lines each subproject must additionally cover to clear each rung, cumulative from today:

| Subproject | now | → 0.30 | → 0.50 | → 0.70 | → 0.90 |
|---|---:|---:|---:|---:|---:|
| `modules/reporting` | 19.8% | 32 | 97 | 162 | 226 |
| `modules/tenancy` | 21.1% | 65 | 214 | 363 | 511 |
| `boot/app` | 25.3% | 30 | 158 | 286 | 414 |
| `modules/notification` | 41.4% | — | 29 | 96 | 164 |
| `modules/learning` | 46.2% | — | 45 | 286 | 528 |
| `modules/ai` | 47.4% | — | 13 | 116 | 219 |
| `modules/simulation` | 50.3% | — | — | 66 | 133 |
| `modules/auth` | 52.7% | — | — | 92 | 200 |
| `shared/security` | 53.2% | — | — | 7 | 17 |
| `modules/interception` | 56.0% | — | — | 25 | 62 |
| `shared/tenant-context` | 60.5% | — | — | 10 | 33 |
| `modules/analytics` | 74.3% | — | — | — | 48 |
| **Total additional lines** | | **127** | **556** | **1509** | **2555** |

**Rung 1 costs 127 lines across three subprojects.** That is a week's work for one group,
not a semester for twenty. The programme does not begin with a wall.

The cost is not linear: 0.70 → 0.90 alone accounts for **1046** of the 2555 lines, because
the last stretch is error paths, edge cases and branches that ordinary use never reaches.
Budget accordingly.

## 4. Who raises what

The floor is an **acceptance criterion of the topic that touches the module** — not a task
assigned to one group. Topics from `digishield/STUDENT_TOPICS.html`:

| Subproject | Topics that own it |
|---|---|
| `modules/reporting` | ĐT07 · ĐT08 · ĐT13 |
| `modules/tenancy` | ĐT16 · ĐT18 |
| `boot/app` | ĐT15 (composition root and the integration suite) |
| `modules/notification` | ĐT02 · ĐT17 |
| `modules/learning` | ĐT09 · ĐT10 · ĐT11 |
| `modules/ai` | ĐT05 · ĐT08 · ĐT14 |
| `modules/simulation` | ĐT01 – ĐT06 |
| `modules/auth` | ĐT18 · ĐT20 |
| `shared/security` · `shared/tenant-context` | ĐT20 |
| `modules/interception` | ĐT07 · ĐT08 |
| `modules/analytics` | ĐT12 · ĐT13 |

**ĐT15 does not raise coverage on behalf of others.** It owns the measurement: wiring
integration coverage in, adding PITest, raising the floor when a rung is cleared, and
refusing the rise when it is not.

## 5. Raising the floor

The floor lives in `digishield/buildSrc/src/main/kotlin/digishield.spring-module-conventions.gradle.kts`
and its `spring-boot-app` sibling.

Procedure, and it is deliberately conservative:

1. Run `./gradlew test jacocoTestReport` and read every subproject.
2. Raise the floor **only if the weakest subproject clears the new value with margin** —
   at least three points, so an ordinary refactor does not turn the build red.
3. Change both convention plugins in the same commit; they must never disagree.
4. State the measured minimum in the commit message, so the next person can see what the
   floor was set against.

Never raise the floor above the weakest module in the hope that somebody will catch up. A
red build that nobody caused is a red build everybody learns to ignore.

## 6. What this does not promise

Ninety per cent line coverage does not mean the software is correct. It means ninety per
cent of lines were executed by some test. A suite can reach 0.90 and assert almost nothing —
which is exactly why PITest is on the roadmap and why the five mandatory test layers exist
alongside the number rather than under it.
