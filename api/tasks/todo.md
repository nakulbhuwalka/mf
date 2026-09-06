# Task Checklist: Mutual Fund Data API

## Phase 0: Foundations, Liquibase Schema & OpenAPI Specification
- [x] Task 0.1: Maven Project Setup with Liquibase, PMD Plugin, Java 25 & Spring Boot 4.1.1 (Persona: `spring-boot-developer`)
  - Acceptance: `pom.xml` configured with Java 25 compiler target, Spring Boot 4.1.1, `liquibase-core`, `maven-pmd-plugin` (rulesets: bestpractices, errorprone, performance), dependencies (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `h2`, `rest-assured`, `validation`); `mvn clean compile` and `mvn pmd:check` succeed.
  - Verify: `mvn compile` and `mvn pmd:check`
  - Files: `pom.xml`, `.gitignore`, `src/main/resources/application.yml`
- [x] Task 0.2: Liquibase Schema Migration Changesets (Persona: `spring-boot-developer`)
  - Acceptance: Liquibase changesets define lean `schemes` and `nav_records` tables (no timestamps, no latest nav in schemes, `nav_date INT`, `nav_value FLOAT`, composite PK on `nav_records`, FK, and indexes).
  - Verify: Liquibase executes on Spring Boot startup and creates tables and `DATABASECHANGELOG`.
  - Files: `src/main/resources/db/changelog/db.changelog-master.yaml`, `src/main/resources/db/changelog/changes/001-create-schemes.yaml`, `src/main/resources/db/changelog/changes/002-create-nav-records.yaml`
- [x] Task 0.3: OpenAPI 3.1 Specification Authoring (Persona: `api-test-engineer`)
  - Acceptance: Complete OpenAPI 3.1 spec covering `/api/v1/schemes`, `/api/v1/schemes/{scheme_code}/nav`, `/api/v1/admin/schemes/sync`, schemas, query parameters, error responses.
  - Verify: Valid OpenAPI syntax.
  - Files: `src/main/resources/openapi.yaml`
- [x] Task 0.4: Base REST Assured Fixture & Liquibase Migration Test (Persona: `api-test-engineer`)
  - Acceptance: Test verifies Spring Boot application context loads, Liquibase runs changesets, and H2 database initializes with lean schema.
  - Verify: `mvn test -Dtest=LiquibaseMigrationTest`
  - Files: `src/test/java/com/mf/api/BaseApiTest.java`, `src/test/java/com/mf/api/LiquibaseMigrationTest.java`

## Checkpoint 0
- [x] Project compiles with Java 25
- [x] Liquibase successfully runs migrations creating lean schema
- [x] `mvn verify` passes PMD checks without violations
- [x] OpenAPI spec is complete and compliant

---

## Phase 1: Admin Scheme Catalog Ingestion API
- [x] Task 1.1: Entity & DTO Models for Schemes (Persona: `spring-boot-developer`)
  - Acceptance: `Scheme` JPA entity matching lean schema, `SchemeRepository`, upstream Jackson DTOs for `mf/latest`. Code passes PMD.
  - Verify: Unit test mapping upstream JSON to entity.
  - Files: `Scheme.java`, `SchemeRepository.java`, `UpstreamSchemeItemDto.java`, `SyncSummaryDto.java`
- [x] Task 1.2: Upstream HTTP Client for Schemes (Persona: `spring-boot-developer`)
  - Acceptance: Spring `RestClient` configured with 15s timeouts; `fetchLatestSchemes()` parses ~38k schemes.
  - Verify: Unit test with `MockRestServiceServer`.
  - Files: `MfApiClient.java`, `AppConfig.java`
- [x] Task 1.3: High-Speed Bulk Ingestion Service (Persona: `spring-boot-developer`)
  - Acceptance: `JdbcTemplate.batchUpdate` bulk inserts schemes in 2k chunks with upsert logic; returns sync summary.
  - Verify: Benchmark test confirms 1k records ingested in < 200ms.
  - Files: `SchemeSyncService.java`
- [x] Task 1.4: Admin Controller Endpoint (Persona: `spring-boot-developer`)
  - Acceptance: `POST /api/v1/admin/schemes/sync` triggers sync service, returns 200 OK with `SyncSummaryDto`.
  - Verify: MockMvc controller test.
  - Files: `AdminController.java`
- [x] Task 1.5: Unit Testing for Ingestion Components (Persona: `api-test-engineer`)
  - Acceptance: Unit tests for `MfApiClientTest` and `SchemeSyncServiceTest` with mocked server and error scenarios.
  - Verify: `mvn test -Dtest=SchemeSyncServiceTest,MfApiClientTest`
  - Files: `SchemeSyncServiceTest.java`, `MfApiClientTest.java`
- [x] Task 1.6: REST Assured Functional Tests for Admin Sync (Persona: `api-test-engineer`)
  - Acceptance: REST Assured tests verify HTTP 200 status, sync summary payload, and scheme records in DB.
  - Verify: `mvn test -Dtest=AdminSyncFunctionalTest`
  - Files: `AdminSyncFunctionalTest.java`

## Checkpoint 1
- [x] Admin sync successfully ingests scheme catalog into H2
- [x] PMD analysis passes clean
- [x] All unit and REST Assured functional tests pass

---

## Phase 2: Scheme Catalog Listing & Search API
- [x] Task 2.1: Dynamic Query Specification & Pagination Logic (Persona: `spring-boot-developer`)
  - Acceptance: `SchemeSpecifications` supports case-insensitive name search, `fundHouse`, `schemeType`, and `schemeCategory` filters with Spring Data `Pageable`.
  - Verify: Repository query tests with various criteria.
  - Files: `SchemeSpecifications.java`, `SchemeResponseDto.java`, `PageResponseDto.java`
- [x] Task 2.2: Scheme Catalog Service & Controller (Persona: `spring-boot-developer`)
  - Acceptance: `GET /api/v1/schemes` returns 200 OK with paged items; default page=0, size=50.
  - Verify: MockMvc tests for `GET /api/v1/schemes`.
  - Files: `SchemeQueryService.java`, `SchemeController.java`
- [x] Task 2.3: Unit Tests for Scheme Query Logic (Persona: `api-test-engineer`)
  - Acceptance: Unit tests covering search edge cases, pagination boundary conditions, and multi-filter combinations.
  - Verify: `mvn test -Dtest=SchemeQueryServiceTest`
  - Files: `SchemeQueryServiceTest.java`
- [x] Task 2.4: REST Assured Functional API Tests for Scheme Catalog (Persona: `api-test-engineer`)
  - Acceptance: REST Assured suite validates pagination, name search, fund house filtering, and empty page handling.
  - Verify: `mvn test -Dtest=SchemeCatalogFunctionalTest`
  - Files: `SchemeCatalogFunctionalTest.java`
- [x] Task 2.5: Performance Test Suite for Scheme Catalog (Persona: `perf-test-engineer`)
  - Acceptance: Load test harness simulates 20 concurrent clients against 38k records; validates p95 latency < 20ms.
  - Verify: `mvn test -Dtest=SchemeCatalogPerfTest`
  - Files: `src/test/java/com/mf/api/perf/SchemeCatalogPerfTest.java`

## Checkpoint 2
- [x] `GET /api/v1/schemes` functional with search and filters
- [x] PMD rulesets satisfied without error
- [x] REST Assured suite green
- [x] Scheme catalog p95 latency < 20ms under load

---

## Phase 3: Historical NAV API with Lazy Loading
- [x] Task 3.1: NavRecord Entity, Composite Key & Conversion Utilities (Persona: `spring-boot-developer`)
  - Acceptance: `NavRecord` (3 columns: `scheme_code INT`, `nav_date INT`, `nav_value FLOAT`) with composite key `NavRecordId`; `DateEpochUtils` converts `DD-MM-YYYY` <-> epoch day int <-> `YYYY-MM-DD`.
  - Verify: Unit test for `DateEpochUtils`.
  - Files: `NavRecord.java`, `NavRecordId.java`, `NavRecordRepository.java`, `DateEpochUtils.java`
- [x] Task 3.2: Upstream NAV Fetcher in MfApiClient (Persona: `spring-boot-developer`)
  - Acceptance: `fetchSchemeNav(int schemeCode)` calls `https://api.mfapi.in/mf/{scheme_code}`; parses meta and data points; handles upstream 404 cleanly.
  - Verify: Unit test with `MockRestServiceServer`.
  - Files: `MfApiClient.java`, `UpstreamNavHistoryDto.java`
- [x] Task 3.3: Lazy-Loading NAV Service with Stampede Prevention (Persona: `spring-boot-developer`)
  - Acceptance: Checks `nav_synced`; on cache miss, uses per-scheme mutex, fetches upstream, bulk inserts via `JdbcTemplate.batchUpdate`, marks `nav_synced = true`; runs integer range query for date filtering; formats dates as `YYYY-MM-DD` and nav as float.
  - Verify: Service unit test for cache miss vs hit.
  - Files: `NavService.java`, `NavResponseDto.java`
- [x] Task 3.4: NAV Controller & Global Exception Handling (Persona: `spring-boot-developer`)
  - Acceptance: `GET /api/v1/schemes/{scheme_code}/nav` endpoint; uniform error payload for 400 (invalid dates), 404 (scheme not found), 502 (upstream error).
  - Verify: MockMvc error scenario tests.
  - Files: `NavController.java`, `GlobalExceptionHandler.java`, `ErrorResponseDto.java`
- [x] Task 3.5: Unit Tests for NAV Logic & Date Conversions (Persona: `api-test-engineer`)
  - Acceptance: Comprehensive unit test suite for date conversions, date range slicing, and float precision.
  - Verify: `mvn test -Dtest=NavServiceTest,DateEpochUtilsTest`
  - Files: `NavServiceTest.java`, `DateEpochUtilsTest.java`
- [x] Task 3.6: REST Assured Functional API Tests for NAV (Persona: `api-test-engineer`)
  - Acceptance: REST Assured tests verify lazy-loading on first call, cache hit on second call, date range filtering, invalid scheme 404, and date format 400.
  - Verify: `mvn test -Dtest=NavFunctionalTest`
  - Files: `NavFunctionalTest.java`
- [x] Task 3.7: Performance & Concurrency Test Suite for NAV API (Persona: `perf-test-engineer`)
  - Acceptance: Concurrency test verifies 50 concurrent requests for uncached scheme fire only 1 upstream request; latency test confirms cached NAV retrieval p95 < 15ms.
  - Verify: `mvn test -Dtest=NavPerfTest`
  - Files: `src/test/java/com/mf/api/perf/NavPerfTest.java`

## Checkpoint 3
- [x] Lazy loading persists NAVs permanently in DB
- [x] Dates formatted as `YYYY-MM-DD` strings, NAV as float
- [x] Cached NAV p95 latency < 15ms
- [x] PMD and CPD checks pass
- [x] All REST Assured functional tests pass

---

## Phase 4: Full System Verification, Benchmarking & Packaging
- [x] Task 4.1: Comprehensive End-to-End System Test (Persona: `api-test-engineer`)
  - Acceptance: REST Assured test runs full flow: Admin sync -> Scheme catalog search -> NAV lazy load -> Filtered NAV query.
  - Verify: `mvn test -Dtest=EndToEndIntegrationTest`
  - Files: `EndToEndIntegrationTest.java`
- [x] Task 4.2: Performance Benchmarking Suite & Latency Report (Persona: `perf-test-engineer`)
  - Acceptance: Mixed workload stress test (80% scheme queries, 20% NAV queries); writes `docs/benchmark-report.md` with p50, p90, p95, p99, RPS, and memory profiles.
  - Verify: `mvn test -Dtest=BenchmarkSuite`
  - Files: `src/test/java/com/mf/api/perf/BenchmarkSuite.java`, `docs/benchmark-report.md`
- [x] Task 4.3: Production Packaging, PMD Quality Verification & Run Verification (Persona: `spring-boot-developer`)
  - Acceptance: `mvn clean verify` passes all PMD and CPD quality gates, compiles fat JAR; verified runnable via `java -jar target/mf-api-0.0.1-SNAPSHOT.jar`; README includes curl examples and OpenAPI link.
  - Verify: `mvn clean verify` succeeds with 0 PMD violations.
  - Files: `README.md`, `pom.xml`

## Checkpoint 4
- [x] Production build succeeds with 0 PMD violations
- [x] 100% of unit, REST Assured functional, and performance tests pass
- [x] Fat JAR packages and runs cleanly

---

## Phase 5: Code Review & Quality Audit
- [x] Task 5.1: Multi-Axis Codebase Review (Persona: `code-reviewer`)
  - Acceptance: Independent review covering correctness, readability, architecture, security, and performance; writes `docs/code-review-report.md`.
  - Verify: Generated review report approved.
  - Files: `docs/code-review-report.md`
- [x] Task 5.2: PMD Quality Gate & OpenAPI Compliance Verification (Persona: `code-reviewer`)
  - Acceptance: Verifies 0 PMD violations/suppressions, 100% adherence to `openapi.yaml`, test coverage > 90%.
  - Verify: PMD report analysis and contract review.
  - Files: `docs/code-review-report.md`
- [x] Task 5.3: Remediate Code Review Findings (Persona: `spring-boot-developer` & `code-reviewer`)
  - Acceptance: All findings addressed; clean build with `mvn clean verify`.
  - Verify: `mvn clean verify` succeeds with 0 PMD violations.
  - Files: As needed

## Checkpoint 5 (Final Sign-Off)
- [x] Code review completed and findings resolved
- [x] Final `mvn clean verify` green
- [x] Ready for deployment
