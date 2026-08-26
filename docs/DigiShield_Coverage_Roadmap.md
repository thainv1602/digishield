# DigiShield — Coverage roadmap to 0.90

> Version 1.3 · 11/08/2026 — rungs one and two cleared; the floor is 0.50
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
| `boot/app` | 240 / 640 | 37.5% |
| `modules/notification` | 140 / 338 | 41.4% |
| `modules/learning` | 558 / 1207 | 46.2% |
| `modules/ai` | 244 / 515 | 47.4% |
| `modules/simulation` | 169 / 336 | 50.3% |
| `modules/auth` | 283 / 537 | 52.7% |
| `shared/security` | 25 / 47 | 53.2% |
| `modules/interception` | 103 / 184 | 56.0% |
| `shared/tenant-context` | 69 / 114 | 60.5% |
| `modules/analytics` | 231 / 311 | **74.3%** |
| **Total (per-module, unit suites)** | **2283 / 5295** | **43.1%** |
| **Union incl. integration suite** | **3189 / 5553** | **57.4%** |

The current floor is `0.50`. The weakest subprojects sit at 50.4%, so the gate is at
what is already true rather than under it — deliberately, since the alternative was
leaving a rung's worth of work uncommitted, but it means an ordinary refactor can now
turn the build red.

## 2. Rung zero — done, and it changed the numbers

**The measurement was wrong, and correcting it was worth more than any rung.**

`boot/app` reported 25.3% because its `jacocoTestReport` saw only the unit suite's exec
data; the whole Testcontainers suite was invisible. Two defects, both now fixed:

- Report and gate take **every `*.exec` file**, so `check` counts both suites. Deliberately
  without a task dependency, so `./gradlew test` still runs with no Docker daemon — which is
  what the `backend` CI job relies on.
- `boot/app` had **`jacocoTestCoverageVerification { enabled = false }`**. Its gate was not
  merely low, it was switched off. Re-enabled; it passes.
- The root aggregation declared only `testSuiteName = "test"`. A second report,
  `integrationTestCodeCoverageReport`, now exists.

What that revealed, measured on a clean build:

| | Lines | % |
|---|---|---|
| Unit suite alone | 2240 / 5553 | 40.3% |
| Integration suite alone | 1680 / 5553 | 30.3% |
| **Union of both** | **3189 / 5553** | **57.4%** |

The integration suite covers **949 lines that no unit test touches**. The project was never
at 40.3%; it was at 57.4% and measuring itself wrongly. `boot/app` alone went 25.3% → 37.5%
without a line of new test code.

**The remaining gap to 0.90 is 1809 lines, not 2555** — 29% less than this document
claimed in version 1.0.

> Per-module gates still measure each module's own unit suite, and that is deliberate: a
> module's gate should fail for that module's own missing tests, not pass because an
> integration test in `boot/app` happened to walk through it. The union figure above is the
> project-health number; the per-module figures are the accountability ones.

Two smaller measurement rules, so the number keeps meaning something:

- The exclusion list stays closed — generated sources, framework configuration,
  `package-info`. Nothing is excluded for being inconvenient to test.
- Coverage measures execution, not assertion. `./gradlew check` also runs **SpotBugs** and
  **Checkstyle**. **PITest** is not wired yet and belongs to no topic since the quality
  topic was dropped: mutation score is what distinguishes a
  test that checks something from a test that merely runs it.

## 3. The rungs

Lines each subproject must additionally cover to clear each rung, cumulative from today:

| Subproject | now | → 0.70 | → 0.90 |
|---|---:|---:|---:|
| `modules/simulation` | 50.4% | 70 | 140 |
| `boot/app` | 50.4% | 136 | 274 |
| `modules/tenancy` | 50.7% | 144 | 293 |
| `modules/learning` | 50.7% | 233 | 475 |
| `modules/ai` | 52.3% | 92 | 195 |
| `modules/auth` | 52.8% | 93 | 201 |
| `shared/security` | 53.2% | 8 | 18 |
| `modules/reporting` | 55.2% | 54 | 126 |
| `modules/notification` | 55.2% | 51 | 120 |
| `modules/interception` | 56.0% | 26 | 63 |
| `shared/tenant-context` | 66.9% | 4 | 28 |
| `modules/analytics` | 74.0% | — | 51 |
| **Total additional lines** | | **911** | **1984** |

**Rungs 1 and 2 are done.** The floor is 0.50 and every subproject clears it; the
weakest are `modules/simulation` and `boot/app` at 50.4%, so the margin is thin and the
next change to either is the one to watch. Rung 3 costs the lines in the 0.70 column.

The cost is not linear: 0.70 → 0.90 alone accounts for **1046** of the 2555 lines, because
the last stretch is error paths, edge cases and branches that ordinary use never reaches.
Budget accordingly.

## 4. Who raises what

The floor is an **acceptance criterion of the topic that touches the module** — not a task
assigned to one group. Topics from the lecturer's STUDENT_TOPICS handout, kept outside
this repository:

| Subproject | Topics that own it |
|---|---|
| `modules/reporting` | ĐT7 · ĐT8 · ĐT13 |
| `modules/tenancy` | ĐT15 · ĐT17 |
| `boot/app` | unassigned (composition root and the integration suite) |
| `modules/notification` | ĐT2 · ĐT16 |
| `modules/learning` | ĐT9 · ĐT10 · ĐT11 |
| `modules/ai` | ĐT5 · ĐT8 · ĐT14 |
| `modules/simulation` | ĐT1 – ĐT6 |
| `modules/auth` | ĐT17 · ĐT19 |
| `shared/security` · `shared/tenant-context` | ĐT19 |
| `modules/interception` | ĐT7 · ĐT8 |
| `modules/analytics` | ĐT12 · ĐT13 |

**Nobody owns the measurement.** The quality topic that used to hold it -- adding
PITest, raising the floor when a rung is cleared, refusing the rise when it is not --
has been dropped from the capstone list, so that work is currently unassigned. Wiring
integration coverage into the aggregate is already done.

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
