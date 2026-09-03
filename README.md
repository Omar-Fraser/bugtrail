# BugTrail

A defect tracking system with a computed triage engine.

Most bug trackers ask the person filing the report to pick a priority. Reporters can't see how many users are affected, whether the behaviour worked in a previous release, or which components sit on the critical path — so what you get back is noise. BugTrail computes priority from five weighted inputs instead, and enforces ticket state changes through a validated state machine rather than an unguarded dropdown.

**Stack:** Java 21 · Spring Boot 4.1 · PostgreSQL 17 · Flyway · JUnit 5 · Testcontainers · PITest

---

## Status

Week 1 scaffold. The triage engine and transition validator are implemented and tested; entities, repositories, and the REST API are not yet built.

---

## Getting started

### 1. Add the Gradle wrapper

The wrapper files aren't in this scaffold, so add them once before your first build.

**If you have Gradle installed:**

```bash
gradle wrapper --gradle-version 8.14.3
```

**If you don't:** open the folder in IntelliJ IDEA and it will generate the wrapper automatically when it imports the Gradle project. Alternatively, generate a throwaway project at [start.spring.io](https://start.spring.io) and copy `gradlew`, `gradlew.bat`, and the `gradle/` folder out of the zip.

### 2. Start the database

```bash
docker compose up -d
```

Postgres 17 on port 5432, database `bugtrail`, user `bugtrail`, password `bugtrail`. Local development credentials only — they never leave this file.

### 3. Build and test

```bash
./gradlew build          # compile, test, coverage report
./gradlew test           # tests only
./gradlew pitest         # mutation testing (slower)
./gradlew bootRun        # start the app on :8080
```

Reports land in `build/reports/` — `tests/test/index.html` for results, `jacoco/test/html/index.html` for coverage, `pitest/index.html` for mutation score.

---

## How triage works

Five normalized inputs, each scored 0–100, combined into a weighted score and bucketed:

```
score = 0.40 × severity              BLOCKER 100 · CRITICAL 80 · MAJOR 60 · MINOR 35 · TRIVIAL 15
      + 0.25 × reach                 100 × log10(1 + 9 × affectedFraction)
      + 0.15 × reproducibility       ALWAYS 100 · INTERMITTENT 60 · ONCE 25
      + 0.12 × regression            REGRESSION 100 · NEW_DEFECT 40
      + 0.08 × componentCriticality  CRITICAL_PATH 100 · CORE 70 · PERIPHERAL 40

P0  score ≥ 80    block the release, fix now
P1  score ≥ 60    this sprint
P2  score ≥ 40    next release
P3  score <  40   icebox
```

Reach is log-scaled because the jump from 1% of users to 10% matters far more than the jump from 80% to 90%. Linear scaling would let widespread-but-cosmetic defects crowd out narrow catastrophic ones.

One override rule: a `BLOCKER` on a `CRITICAL_PATH` component is always P0 regardless of score. A total failure of authentication that only one person has reported scores 56.55 and would otherwise sit in P2.

**Severity is not priority.** Severity describes the defect and is set by the reporter. Priority describes the schedule and is computed. They are separate columns in the schema because they routinely disagree.

---

## Ticket lifecycle

```
NEW → TRIAGED → ASSIGNED → IN_PROGRESS → IN_REVIEW → VERIFIED → CLOSED
       │                                                           │
       ├→ REJECTED / DUPLICATE / WONT_FIX  (terminal)              │
       └───────────────── reopen ───────────────────────────────────┘
```

Fourteen legal transitions out of 100 possible ordered pairs. `TransitionValidatorTest` asserts all 100.

Two rules worth knowing:

- Terminal outcomes are reachable only from `TRIAGED`. You can't reject a ticket nobody has looked at.
- Reopening returns to `TRIAGED`, not `IN_PROGRESS` — the conditions that set the original priority have almost certainly changed.

---

## Test strategy

| Layer | Share | Tools | Covers |
|---|---|---|---|
| Unit | ~70% | JUnit 5, Mockito, AssertJ | Triage scoring, transition legality, validators. No Spring context, no database. |
| Integration | ~20% | `@SpringBootTest`, Testcontainers | Repositories against real PostgreSQL. Not H2 — H2 accepts SQL that Postgres rejects. |
| API contract | ~7% | RestAssured, Schemathesis | Status codes, payload shapes, auth boundaries. |
| End-to-end | ~3% | Playwright | Five critical journeys only. |

Coverage is gated at 70% and mutation score at 75%. Line coverage only proves a line executed — a test with no assertions still counts toward it. PITest deliberately corrupts the code and reports how many corruptions the suite caught.

---

## Layout

```
src/main/java/com/omarfraser/bugtrail/
├── domain/      Severity, Priority, TicketStatus, and the scoring enums
├── triage/      TriageService, TriageInput, TriageResult, TriageWeights
└── workflow/    TransitionValidator, IllegalTransitionException

src/main/resources/
├── application.yml
└── db/migration/V1__init.sql
```

The triage and workflow packages have no Spring annotations on purpose. Keeping the framework out of the domain logic is what lets their tests run without a context, which is why the unit layer finishes in under a second. They're registered as beans in `BugTrailApplication`.

---

## Next up

- [ ] Entities, repositories, and DTOs for the V1 schema
- [ ] REST API with Bean Validation and RFC 9457 problem details
- [ ] `@RestControllerAdvice` mapping `IllegalTransitionException` to 409
- [ ] springdoc-openapi for a self-documenting API
- [ ] Testcontainers integration tests for the repository layer
