# Implementation Plan: Mutual Fund Data API (Java 25 / Spring Boot 4+)

## Overview
Build a high-throughput, locally cached REST API using **Java 25** and **Spring Boot 4.1.1** to retrieve, index, and query Indian Mutual Fund data from [mfapi.in](https://www.mfapi.in). The system features an admin ingestion pipeline for ~38,000 schemes, on-demand lazy loading for historical NAV records, a lean database schema managed via **Liquibase**, **PMD static code analysis** quality gates, and a final review phase executed by an autonomous **Code Reviewer Agent**.

---

## Agent Personas & Parallelization Strategy

To maximize velocity, rigor, and separation of concerns, tasks are assigned to four specialized agent personas:

| Persona | Primary Focus | Key Deliverables |
|---|---|---|
| **Spring Boot / Java API Developer** (`spring-boot-developer`) | Core backend engineering | Maven build configuration with Liquibase & PMD plugins, Liquibase changelogs, database entities, repositories, services, controllers, batch ingestion pipeline |
| **API Test Engineer** (`api-test-engineer`) | Quality, contracts & functional testing | OpenAPI 3.1 specification, unit test suites (JUnit 5, Mockito), REST Assured functional API test suites |
| **Performance Test Engineer** (`perf-test-engineer`) | Performance, concurrency & benchmarking | Load test harnesses, concurrency stress testing, response time validation (p95 < 20ms for schemes, p95 < 15ms for cached NAV) |
| **Senior Code Reviewer** (`code-reviewer`) | Multi-axis code review & quality audit | Comprehensive review across correctness, readability, architecture, performance, and security; verification of PMD reports and test coverage |

---

## Architecture & Technical Decisions

1. **Java 25 & Spring Boot 4.1.1**:
   - Modern records for immutable DTOs.
   - Spring 6+ `RestClient` with timeout resilience for upstream communication.
2. **Database Migrations (Liquibase)**:
   - Version-controlled schema migrations using `liquibase-core` with master changelog `db/changelog/db.changelog-master.yaml`.
   - Automated migration execution on application startup.
3. **Lean Database Schema**:
   - `schemes`: Pure metadata table (no timestamps, no latest NAV values).
   - `nav_records`: 3-column table `(scheme_code, nav_date, nav_value)` with composite PK.
   - Dates stored as 32-bit `INT` (days from Unix epoch `1970-01-01`).
   - NAV stored as 32-bit `FLOAT`.
4. **Code Quality Gates (PMD & CPD)**:
   - Automated PMD static analysis (`maven-pmd-plugin` 3.28.0) bound to the `verify` lifecycle phase.
   - Rulesets enforced: `bestpractices`, `errorprone`, `performance`.
   - Copy-Paste Detector (`cpd-check`) fails build if code duplication exceeds threshold.
5. **Data Ingestion Performance**:
   - `JdbcTemplate.batchUpdate` with 2,000-row batching for ingesting ~38,000 schemes in < 2.5s.
6. **Lazy-Loading & Concurrency Guard**:
   - On-demand fetch on first access of scheme NAV, stored permanently (`nav_synced = true`).
   - Per-scheme concurrent mutex (`ConcurrentHashMap<Integer, Object>`) to eliminate cache stampedes under concurrent traffic.
7. **Testing Architecture**:
   - **Unit Testing**: JUnit 5, Mockito, Spring `MockRestServiceServer` for isolating upstream HTTP calls.
   - **Functional Testing**: REST Assured against a live local server port (`@SpringBootTest(webEnvironment = RANDOM_PORT)`).
   - **Performance Testing**: Java/HttpClient concurrent stress harness measuring p50, p90, p95, p99 latencies and RPS.
8. **Final Code Review Gate**:
   - Independent audit by `code-reviewer` persona before sign-off.

---

## Phases and Tasks

### Phase 0: Foundations, Liquibase Schema & OpenAPI Specification
*Goal: Establish project structure, Liquibase database schema, PMD quality gates, and definitive OpenAPI 3.1 contract.*

- [x] **Task 0.1**: Maven Project Setup with Liquibase & PMD Plugins (Java 25 & Spring Boot 4.1.1)
  - **Persona**: `spring-boot-developer`
  - **Files**: `pom.xml`, `.gitignore`, `src/main/resources/application.yml`
  - **Acceptance**: `mvn clean compile` succeeds with Java 25 compiler target and Spring Boot 4.1.1 parent; includes `liquibase-core`, `maven-pmd-plugin` (rulesets: bestpractices, errorprone, performance), `rest-assured`, `h2`, `spring-boot-starter-data-jpa`, `spring-boot-starter-web`.
  - **Verify**: `mvn compile` and `mvn pmd:check`
- [x] **Task 0.2**: Liquibase Schema Migration Changesets
  - **Persona**: `spring-boot-developer`
  - **Files**:
    - `src/main/resources/db/changelog/db.changelog-master.yaml`
    - `src/main/resources/db/changelog/changes/001-create-schemes.yaml`
    - `src/main/resources/db/changelog/changes/002-create-nav-records.yaml`
  - **Acceptance**: Liquibase changesets define lean `schemes` (no timestamps, no latest nav) and `nav_records` (`nav_date INT`, `nav_value FLOAT`, composite PK, FK to schemes, indexes on `scheme_name`, `fund_house`, `nav_date`).
  - **Verify**: Liquibase migration executes on Spring Boot startup and creates tables and `DATABASECHANGELOG`.
- [x] **Task 0.3**: OpenAPI 3.1 Specification Authoring
  - **Persona**: `api-test-engineer`
  - **Files**: `src/main/resources/openapi.yaml`
  - **Acceptance**: Complete OpenAPI spec defining `GET /api/v1/schemes`, `GET /api/v1/schemes/{scheme_code}/nav`, `POST /api/v1/admin/schemes/sync`, all query params, error envelopes, and DTO schemas.
  - **Verify**: Validate schema syntax via OpenAPI parser or linter.
- [x] **Task 0.4**: Base REST Assured Test Fixture & Liquibase Migration Test
  - **Persona**: `api-test-engineer`
  - **Files**: `src/test/java/com/mf/api/BaseApiTest.java`, `src/test/java/com/mf/api/LiquibaseMigrationTest.java`
  - **Acceptance**: Test validates that Spring Boot application context boots, Liquibase runs changesets, and H2 database initializes with expected columns and constraints.
  - **Verify**: `mvn test -Dtest=LiquibaseMigrationTest`

#### Checkpoint 0
- [x] Project compiles with Java 25.
- [x] Liquibase successfully runs migrations creating lean schema.
- [x] `mvn verify` passes PMD checks without violations.
- [x] OpenAPI spec is complete and compliant.

---

### Phase 1: Admin Scheme Catalog Ingestion API (`POST /api/v1/admin/schemes/sync`)
*Goal: Implement and verify high-throughput ingestion of ~38,000 mutual fund schemes from mfapi.in.*

- [x] **Task 1.1**: Entity & DTO Models for Schemes
  - **Persona**: `spring-boot-developer`
  - **Files**: `Scheme.java`, `SchemeRepository.java`, `UpstreamSchemeItemDto.java`, `SyncSummaryDto.java`
  - **Acceptance**: JPA entity and repository for `schemes` table; Jackson DTO mapping `mf/latest` upstream payload. Code passes PMD.
  - **Verify**: Entity mapping tests pass.
- [x] **Task 1.2**: Upstream HTTP Client (`MfApiClient`)
  - **Persona**: `spring-boot-developer`
  - **Files**: `MfApiClient.java`, `AppConfig.java`
  - **Acceptance**: `RestClient` configured with connection/read timeouts (15s) and error handling; method `fetchLatestSchemes()` returns parsed scheme list.
  - **Verify**: Unit test with `MockRestServiceServer` simulating upstream response.
- [x] **Task 1.3**: High-Speed Bulk Ingestion Service
  - **Persona**: `spring-boot-developer`
  - **Files**: `SchemeSyncService.java`
  - **Acceptance**: `JdbcTemplate.batchUpdate` ingests scheme list in 2,000-row chunks; handles upsert (`MERGE INTO` in H2); returns sync metrics (`totalFetched`, `inserted`, `durationMs`).
  - **Verify**: Ingestion test verifies 1,000 dummy records upserted in < 200ms.
- [x] **Task 1.4**: Admin Controller Endpoint
  - **Persona**: `spring-boot-developer`
  - **Files**: `AdminController.java`
  - **Acceptance**: Exposes `POST /api/v1/admin/schemes/sync`, invokes sync service, returns `200 OK` with `SyncSummaryDto`. No security headers required.
  - **Verify**: MockMvc controller test.
- [x] **Task 1.5**: Unit Testing for Ingestion Components
  - **Persona**: `api-test-engineer`
  - **Files**: `SchemeSyncServiceTest.java`, `MfApiClientTest.java`
  - **Acceptance**: Tests simulate upstream network failures, partial data, and verify idempotent bulk upserts.
  - **Verify**: `mvn test -Dtest=SchemeSyncServiceTest,MfApiClientTest`
- [x] **Task 1.6**: REST Assured Functional Tests for Admin Sync
  - **Persona**: `api-test-engineer`
  - **Files**: `AdminSyncFunctionalTest.java`
  - **Acceptance**: REST Assured sends `POST /api/v1/admin/schemes/sync` to live local port, validates response HTTP 200, json fields, and verifies schemes exist in DB.
  - **Verify**: `mvn test -Dtest=AdminSyncFunctionalTest`

#### Checkpoint 1
- [x] Admin sync successfully ingests scheme catalog into H2.
- [x] PMD analysis passes clean.
- [x] Unit tests and REST Assured functional tests pass.

---

### Phase 2: Scheme Catalog Listing & Search API (`GET /api/v1/schemes`)
*Goal: Provide sub-20ms paginated query and search across ~38,000 schemes.*

- [x] **Task 2.1**: Dynamic Query Specification & Pagination Logic
  - **Persona**: `spring-boot-developer`
  - **Files**: `SchemeSpecifications.java`, `SchemeRepository.java`, `SchemeResponseDto.java`, `PageResponseDto.java`
  - **Acceptance**: Spring Data JPA Specification supports case-insensitive search on `scheme_name`, exact filters on `fund_house`, `scheme_type`, `scheme_category`, and Spring `Pageable`.
  - **Verify**: Repository query unit tests pass with various filter combinations.
- [x] **Task 2.2**: Scheme Catalog Service & Controller
  - **Persona**: `spring-boot-developer`
  - **Files**: `SchemeQueryService.java`, `SchemeController.java`
  - **Acceptance**: `GET /api/v1/schemes` returns 200 OK with `content` array and `page` metadata; default page=0, size=50 (max 200).
  - **Verify**: MockMvc controller tests for schemes listing.
- [x] **Task 2.3**: Unit Tests for Scheme Query & Filtering
  - **Persona**: `api-test-engineer`
  - **Files**: `SchemeQueryServiceTest.java`
  - **Acceptance**: Tests test pagination boundaries, special characters in search, and multi-field filtering combinations.
  - **Verify**: `mvn test -Dtest=SchemeQueryServiceTest`
- [x] **Task 2.4**: REST Assured Functional API Tests for Scheme Catalog
  - **Persona**: `api-test-engineer`
  - **Files**: `SchemeCatalogFunctionalTest.java`
  - **Acceptance**: REST Assured validates pagination, name search, fund house filtering, and empty page handling.
  - **Verify**: `mvn test -Dtest=SchemeCatalogFunctionalTest`
- [x] **Task 2.5**: Performance Test Suite for Scheme Catalog
  - **Persona**: `perf-test-engineer`
  - **Files**: `src/test/java/com/mf/api/perf/SchemeCatalogPerfTest.java`
  - **Acceptance**: Concurrently executes 200+ requests across 20 threads against loaded ~38k dataset; measures p50, p90, p95 latency. Target: p95 < 20ms.
  - **Verify**: `mvn test -Dtest=SchemeCatalogPerfTest`

#### Checkpoint 2
- [x] `GET /api/v1/schemes` fully functional with pagination and filters.
- [x] PMD rulesets satisfied without error.
- [x] REST Assured suite passes.
- [x] Scheme catalog p95 latency < 20ms under load.

---

### Phase 3: Historical NAV API with Lazy Loading (`GET /api/v1/schemes/{scheme_code}/nav`)
*Goal: Implement on-demand lazy loading from mfapi.in, permanent caching, epoch-day query filtering, and ISO-8601 formatting.*

- [x] **Task 3.1**: NavRecord Entity, Composite Key & Conversion Utilities
  - **Persona**: `spring-boot-developer`
  - **Files**: `NavRecord.java`, `NavRecordId.java`, `NavRecordRepository.java`, `DateEpochUtils.java`
  - **Acceptance**: `NavRecord` maps to 3 columns (`scheme_code INT`, `nav_date INT`, `nav_value FLOAT`); `DateEpochUtils` converts `DD-MM-YYYY` <-> epoch day int <-> `YYYY-MM-DD`.
  - **Verify**: Unit tests for `DateEpochUtils` covering leap years, edge dates, and conversions.
- [x] **Task 3.2**: Upstream Scheme NAV Client Method
  - **Persona**: `spring-boot-developer`
  - **Files**: `MfApiClient.java`, `UpstreamNavHistoryDto.java`
  - **Acceptance**: `fetchSchemeNav(int schemeCode)` calls `https://api.mfapi.in/mf/{scheme_code}`, parses `meta` and `data` array into DTOs; returns 404 if scheme does not exist upstream.
  - **Verify**: Unit test with mocked upstream response.
- [x] **Task 3.3**: Lazy-Loading NAV Service with Stampede Prevention
  - **Persona**: `spring-boot-developer`
  - **Files**: `NavService.java`
  - **Acceptance**:
    - If `scheme.nav_synced == false`: acquires per-scheme lock, calls `MfApiClient`, bulk-inserts NAV records via `JdbcTemplate.batchUpdate`, updates `nav_synced = true`.
    - If `scheme.nav_synced == true`: directly queries local DB.
    - Translates `startDate` and `endDate` to epoch day integers; runs fast range query: `nav_date BETWEEN :start AND :end`.
    - Formats output DTO with ISO-8601 `YYYY-MM-DD` date strings and float NAV values.
  - **Verify**: Service tests for cache hit, cache miss, and date range filtering.
- [x] **Task 3.4**: NAV Controller & Global Exception Handling
  - **Persona**: `spring-boot-developer`
  - **Files**: `NavController.java`, `GlobalExceptionHandler.java`, `ErrorResponseDto.java`
  - **Acceptance**: `GET /api/v1/schemes/{scheme_code}/nav` endpoint; handles invalid date format (400), scheme not found (404), upstream failures (502) with uniform error payload.
  - **Verify**: MockMvc tests for all error scenarios.
- [x] **Task 3.5**: Unit Tests for NAV Service & Date Range Logic
  - **Persona**: `api-test-engineer`
  - **Files**: `NavServiceTest.java`, `DateEpochUtilsTest.java`
  - **Acceptance**: Verifies exact date range slicing, sorting (`asc`/`desc`), and float precision handling.
  - **Verify**: `mvn test -Dtest=NavServiceTest,DateEpochUtilsTest`
- [x] **Task 3.6**: REST Assured Functional API Tests for NAV
  - **Persona**: `api-test-engineer`
  - **Files**: `NavFunctionalTest.java`
  - **Acceptance**:
    - Validates first request triggers lazy loading and returns complete NAV data.
    - Validates subsequent request returns from local DB with identical data.
    - Validates date range filtering (`startDate`, `endDate`) returns expected subset.
    - Validates `404 Not Found` for invalid scheme codes.
    - Validates `400 Bad Request` when `startDate > endDate`.
  - **Verify**: `mvn test -Dtest=NavFunctionalTest`
- [x] **Task 3.7**: Performance & Concurrency Test Suite for NAV API
  - **Persona**: `perf-test-engineer`
  - **Files**: `src/test/java/com/mf/api/perf/NavPerfTest.java`
  - **Acceptance**:
    - Concurrency test: 50 concurrent requests for an un-cached scheme trigger exactly ONE upstream call (stampede guard).
    - Latency test: 500 requests against cached scheme NAV records validate p95 < 15ms.
  - **Verify**: `mvn test -Dtest=NavPerfTest`

#### Checkpoint 3
- [x] Lazy loading persists NAV records permanently in DB.
- [x] Responses format dates as `YYYY-MM-DD` and NAV as float.
- [x] Cache hit response time p95 < 15ms.
- [x] PMD and CPD checks pass.
- [x] All REST Assured functional tests pass.

---

### Phase 4: Full System Verification, Benchmarking & Packaging
*Goal: Run end-to-end user journeys, benchmark overall throughput, verify PMD code quality, and create production artifacts.*

- [x] **Task 4.1**: Comprehensive End-to-End System Test
  - **Persona**: `api-test-engineer`
  - **Files**: `EndToEndIntegrationTest.java`
  - **Acceptance**: Simulates complete user journey: Admin sync -> Scheme catalog search -> Scheme NAV query -> Filtered NAV query. All assertions verified via REST Assured.
  - **Verify**: `mvn test -Dtest=EndToEndIntegrationTest`
- [x] **Task 4.2**: Performance Benchmarking Suite & Latency Report
  - **Persona**: `perf-test-engineer`
  - **Files**: `src/test/java/com/mf/api/perf/BenchmarkSuite.java`, `docs/benchmark-report.md`
  - **Acceptance**: Sustained load test running mixed traffic (80% scheme listing, 20% NAV queries); writes `docs/benchmark-report.md` with p50, p90, p95, p99 latencies, RPS, and memory footprint.
  - **Verify**: `mvn test -Dtest=BenchmarkSuite`
- [x] **Task 4.3**: Production Packaging, PMD Code Quality Verification & Run Verification
  - **Persona**: `spring-boot-developer`
  - **Files**: `README.md`, `pom.xml`
  - **Acceptance**: `mvn clean verify` passes all PMD and CPD quality gates, compiles fat JAR; verified runnable via `java -jar target/mf-api-0.0.1-SNAPSHOT.jar`; README includes curl examples and OpenAPI link.
  - **Verify**: `mvn clean verify` succeeds with 0 PMD violations.

#### Checkpoint 4
- [x] `mvn clean verify` builds with 0 PMD violations.
- [x] 100% of unit, functional (REST Assured), and performance tests pass.
- [x] Application packages and runs cleanly.

---

### Phase 5: Code Review & Quality Audit
*Goal: Independent multi-axis code review by the `code-reviewer` agent persona across correctness, readability, architecture, performance, and security.*

- [x] **Task 5.1**: Multi-Axis Codebase Review
  - **Persona**: `code-reviewer`
  - **Scope**: Full codebase audit (`src/main/java`, `src/main/resources`, `pom.xml`).
  - **Dimensions Evaluated**:
    - **Correctness**: Adherence to specification (dates, lazy loading, error codes).
    - **Readability & Style**: Clean naming, idiomatic Java 25 / Spring Boot 4 patterns, zero redundant code.
    - **Architecture & Modularity**: Clean separation of controller, service, repository, client, and changelogs.
    - **Performance**: Batch sizes, indexing, connection pooling, cache stampede prevention.
    - **Security Readiness**: Input sanitization, SQL injection prevention (parameterized queries).
  - **Deliverable**: `docs/code-review-report.md` detailing findings, strengths, and recommendations.
- [x] **Task 5.2**: PMD Quality Gate & OpenAPI Compliance Verification
  - **Persona**: `code-reviewer`
  - **Scope**: Validate zero PMD/CPD suppressions, 100% adherence to `openapi.yaml` contract, and test suite completeness.
  - **Verify**: Review of PMD report XML/HTML and OpenAPI contract diff.
- [x] **Task 5.3**: Remediate Code Review Findings (if any)
  - **Persona**: `spring-boot-developer` & `code-reviewer`
  - **Acceptance**: Any critical or high-priority findings from the review are resolved and verified with green builds.
  - **Verify**: `mvn clean verify` passes with 0 PMD violations and all tests green.

#### Checkpoint 5 (Final Sign-Off)
- [x] Code review report approved with zero blocking issues.
- [x] `mvn clean verify` passes cleanly with all tests and PMD gates green.
- [x] Ready for production release.
