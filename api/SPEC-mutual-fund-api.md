# Spec: Mutual Fund Data API (Java 25 / Spring Boot 4+)

## 1. Objective

Build a high-performance, locally cached REST API using **Java 25** and **Spring Boot 4+** to retrieve and index Indian Mutual Fund scheme metadata and historical Net Asset Value (NAV) records from [mfapi.in](https://www.mfapi.in).

### Core Endpoints
1. **Scheme Catalog (`GET /api/v1/schemes`)**: Paginated list and search of ~38,000 mutual fund schemes with fund house, category, scheme type, and ISIN codes.
2. **Historical NAV Query (`GET /api/v1/schemes/{scheme_code}/nav`)**: Retrieve NAV history for a scheme given an optional date range (`startDate` to `endDate`), or all available NAV history. Employs **on-demand lazy loading**: fetches from `https://api.mfapi.in/mf/{scheme_code}` on the first request and persists permanently into the local database.
3. **Admin Ingestion (`POST /api/v1/admin/schemes/sync`)**: Ingests and upserts the complete scheme directory into the local database from `https://api.mfapi.in/mf/latest`.

---

## 2. Requirements & MVP Rules

1. **Tech Stack**:
   - **Java Version**: `Java 25` (LTS)
   - **Spring Boot Version**: `4.1.1` (Spring Boot 4+)
   - **Build Tool**: Maven 3.9+ with **PMD Code Analysis (`maven-pmd-plugin`)** enforcing code quality during `verify`
   - **Database Migration**: **Liquibase (`liquibase-core`)** for automated version-controlled schema creation and migrations
   - **Data Access**: Spring Data JPA / JDBC with `JdbcTemplate` for high-throughput batching
   - **Database**: H2 File-based Database (`./data/mfdb`, persisted across restarts, switchable to PostgreSQL)
   - **HTTP Client**: Spring 6+ `RestClient`
2. **Security**: **No security** for MVP (all endpoints are open; no auth/tokens required).
3. **Cache Expiration**: **No cache expiration**. Once NAV data for a scheme is loaded into the local database, it is retained permanently. Subsequent requests for that scheme are served directly from the local database.
4. **Lean Database Schema Rules**:
   - **No timestamps**: Do not capture `created_at` or `updated_at` in the database.
   - **No latest NAV in schemes**: Do not capture latest NAV data (`latest_nav`, `latest_nav_date`) in the `schemes` table; `schemes` stores pure metadata.
   - **NAV Values**: Stored in `nav_records` as **`FLOAT`** (Java `Float`).
   - **NAV Dates**: Stored in `nav_records` as an **`INTEGER` representing days from Unix epoch** (`1970-01-01`), computed via `(int) localDate.toEpochDay()`.
5. **Code Quality & Static Analysis (PMD)**:
   - Automated PMD code checks run on `mvn verify`.
   - Quality rulesets enforced: `bestpractices`, `errorprone`, `performance`.
   - Copy-Paste-Detector (`cpd-check`) prevents duplicated code blocks.
6. **API Response Formatting**:
   - The API **never exposes the raw epoch day integer**. Responses format the date as standard ISO-8601 `YYYY-MM-DD` strings via `LocalDate.ofEpochDay(epochDay).toString()`.
   - Inward query parameters `startDate` and `endDate` accept `YYYY-MM-DD` strings and are converted internally to epoch day integers for fast SQL range scans.

---

## 3. Architecture & Data Flow

```
┌─────────────┐               ┌────────────────────────┐               ┌──────────────────┐
│   Client    │               │ Spring Boot 4 Service  │               │  api.mfapi.in    │
└──────┬──────┘               └───────────┬────────────┘               └────────┬─────────┘
       │                                  │                                     │
       │ (On Startup)                     │ Run Liquibase Migrations            │
       │                                  │ -> Creates `schemes`, `nav_records` │
       │                                  │                                     │
       │ 1. POST /admin/schemes/sync      │                                     │
       ├─────────────────────────────────>│ GET /mf/latest                      │
       │                                  ├────────────────────────────────────>│
       │                                  │ Extract pure scheme metadata        │
       │                                  │ Batch Insert (~38k records)         │
       │                                  │ via JdbcTemplate.batchUpdate        │
       │                                  │<────────────────────────────────────┤
       │ 200 OK (Sync Summary)            │                                     │
       │<─────────────────────────────────┤                                     │
       │                                  │                                     │
       │ 2. GET /schemes?search=HDFC      │                                     │
       ├─────────────────────────────────>│ SELECT from `schemes` (Paged)       │
       │ 200 OK (Paginated Schemes)       │ Local DB only                       │
       │<─────────────────────────────────┤                                     │
       │                                  │                                     │
       │ 3. GET /schemes/125497/nav       │ Check `schemes.nav_synced`          │
       ├─────────────────────────────────>│ If false (Lazy Load):               │
       │                                  │   GET /mf/125497                    │
       │                                  │   ├────────────────────────────────>│
       │                                  │   Convert: DD-MM-YYYY -> Epoch Day  │
       │                                  │   Convert: Nav string -> Float      │
       │                                  │   Batch Insert into nav_records     │
       │                                  │   Update schemes.nav_synced = true  │
       │                                  │   <─────────────────────────────────┤
       │                                  │ Query: nav_date BETWEEN :start AND :end
       │                                  │ Convert Epoch Day -> "YYYY-MM-DD"   │
       │ 200 OK (Meta + NAV list)         │                                     │
       │<─────────────────────────────────┤                                     │
```

---

## 4. API Endpoints Contract

### Base Path
`/api/v1`

### 4.1 Scheme Catalog: `GET /api/v1/schemes`
- **Query Parameters**:
  - `page`: integer (default `0`, 0-indexed)
  - `size`: integer (default `50`, max `200`)
  - `search`: string (case-insensitive search on scheme name)
  - `fundHouse`: string (filter by fund house)
  - `schemeType`: string (e.g. `Open Ended Schemes`)
  - `schemeCategory`: string (e.g. `Equity Scheme - Small Cap Fund`)

- **Response (`200 OK`)**:
  ```json
  {
    "content": [
      {
        "schemeCode": 125497,
        "schemeName": "SBI SMALL CAP FUND - Direct Plan - Growth",
        "fundHouse": "SBI Mutual Fund",
        "schemeType": "Open Ended Schemes",
        "schemeCategory": "Equity Scheme - Small Cap Fund",
        "isinGrowth": "INF200K01T51",
        "isinDivReinvestment": null,
        "navSynced": true
      }
    ],
    "page": {
      "number": 0,
      "size": 50,
      "totalElements": 37860,
      "totalPages": 758
    }
  }
  ```

---

### 4.2 Historical NAV Data: `GET /api/v1/schemes/{scheme_code}/nav`
- **Path Parameters**:
  - `scheme_code`: integer
- **Query Parameters**:
  - `startDate`: string (`YYYY-MM-DD`, optional)
  - `endDate`: string (`YYYY-MM-DD`, optional)
  - `sort`: string (`desc` | `asc`, default: `desc`)
  - `forceRefresh`: boolean (default: `false`)

- **Response (`200 OK`)**:
  ```json
  {
    "meta": {
      "schemeCode": 125497,
      "schemeName": "SBI SMALL CAP FUND - Direct Plan - Growth",
      "fundHouse": "SBI Mutual Fund",
      "schemeType": "Open Ended Schemes",
      "schemeCategory": "Equity Scheme - Small Cap Fund",
      "isinGrowth": "INF200K01T51",
      "isinDivReinvestment": null,
      "totalPoints": 2,
      "startDate": "2026-09-03",
      "endDate": "2026-09-04"
    },
    "data": [
      {
        "date": "2026-09-04",
        "nav": 216.6525
      },
      {
        "date": "2026-09-03",
        "nav": 216.4037
      }
    ]
  }
  ```

---

### 4.3 Admin Scheme Catalog Ingestion: `POST /api/v1/admin/schemes/sync`
- **Headers**: None required for MVP.
- **Response (`200 OK`)**:
  ```json
  {
    "status": "COMPLETED",
    "totalFetched": 37860,
    "inserted": 37860,
    "updated": 0,
    "durationMs": 1800,
    "syncedAt": "2026-09-06T11:15:00Z"
  }
  ```

---

## 5. Liquibase Database Migration Definition

Database schema creation is managed using Liquibase with master changelog located at `src/main/resources/db/changelog/db.changelog-master.yaml`.

### Master Changelog: `db.changelog-master.yaml`
```yaml
databaseChangeLog:
  - include:
      file: db/changelog/changes/001-create-schemes.yaml
  - include:
      file: db/changelog/changes/002-create-nav-records.yaml
```

### Changeset 001: `001-create-schemes.yaml`
```yaml
databaseChangeLog:
  - changeSet:
      id: 001-create-schemes-table
      author: mf-api
      changes:
        - createTable:
            tableName: schemes
            columns:
              - column:
                  name: scheme_code
                  type: int
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: scheme_name
                  type: varchar(500)
                  constraints:
                    nullable: false
              - column:
                  name: fund_house
                  type: varchar(255)
              - column:
                  name: scheme_type
                  type: varchar(100)
              - column:
                  name: scheme_category
                  type: varchar(255)
              - column:
                  name: isin_growth
                  type: varchar(20)
              - column:
                  name: isin_div_reinvestment
                  type: varchar(20)
              - column:
                  name: nav_synced
                  type: boolean
                  defaultValueBoolean: false
                  constraints:
                    nullable: false
        - createIndex:
            tableName: schemes
            indexName: idx_schemes_name
            columns:
              - column:
                  name: scheme_name
        - createIndex:
            tableName: schemes
            indexName: idx_schemes_fund_house
            columns:
              - column:
                  name: fund_house
```

### Changeset 002: `002-create-nav-records.yaml`
```yaml
databaseChangeLog:
  - changeSet:
      id: 002-create-nav-records-table
      author: mf-api
      changes:
        - createTable:
            tableName: nav_records
            columns:
              - column:
                  name: scheme_code
                  type: int
                  constraints:
                    nullable: false
              - column:
                  name: nav_date
                  type: int
                  constraints:
                    nullable: false
              - column:
                  name: nav_value
                  type: float
                  constraints:
                    nullable: false
        - addPrimaryKey:
            tableName: nav_records
            columnNames: scheme_code, nav_date
            constraintName: pk_nav_records
        - addForeignKeyConstraint:
            baseTableName: nav_records
            baseColumnNames: scheme_code
            referencedTableName: schemes
            referencedColumnNames: scheme_code
            constraintName: fk_nav_records_scheme
            onDelete: CASCADE
        - createIndex:
            tableName: nav_records
            indexName: idx_nav_records_date
            columns:
              - column:
                  name: scheme_code
              - column:
                  name: nav_date
```

---

## 6. Maven Configuration (`pom.xml`)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.1</version>
    <relativePath/>
</parent>

<properties>
    <java.version>25</java.version>
    <maven.compiler.source>25</maven.compiler.source>
    <maven.compiler.target>25</maven.compiler.target>
    <pmd.plugin.version>3.28.0</pmd.plugin.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <!-- Liquibase Database Migrations -->
    <dependency>
        <groupId>org.liquibase</groupId>
        <artifactId>liquibase-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.rest-assured</groupId>
        <artifactId>rest-assured</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
        <!-- PMD Code Quality Plugin -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-pmd-plugin</artifactId>
            <version>${pmd.plugin.version}</version>
            <configuration>
                <targetJdk>25</targetJdk>
                <printFailingErrors>true</printFailingErrors>
                <linkXRef>false</linkXRef>
                <rulesets>
                    <ruleset>category/java/bestpractices.xml</ruleset>
                    <ruleset>category/java/errorprone.xml</ruleset>
                    <ruleset>category/java/performance.xml</ruleset>
                </rulesets>
            </configuration>
            <executions>
                <execution>
                    <id>pmd-check</id>
                    <phase>verify</phase>
                    <goals>
                        <goal>check</goal>
                        <goal>cpd-check</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

---

## 7. Success Criteria

- [ ] Liquibase automatically runs changesets on application startup, creating `schemes` and `nav_records` tables and recording state in `DATABASECHANGELOG`.
- [ ] `schemes` table contains zero timestamp columns and zero latest NAV columns.
- [ ] `nav_records` table contains exactly 3 columns: `scheme_code` (int), `nav_date` (int), `nav_value` (float).
- [ ] `POST /api/v1/admin/schemes/sync` ingests ~38,000 schemes in < 2.5 seconds via `JdbcTemplate.batchUpdate`.
- [ ] `GET /api/v1/schemes` returns paginated schemes cleanly.
- [ ] `GET /api/v1/schemes/{scheme_code}/nav` lazy-loads NAVs on first request, persisting in `nav_records` and setting `nav_synced = true`.
- [ ] Subsequent calls to `GET /api/v1/schemes/{scheme_code}/nav` serve directly from the local DB.
- [ ] Dates in API JSON responses are formatted as `"YYYY-MM-DD"` strings.
- [ ] `mvn verify` passes with 0 PMD violations.
- [ ] All unit, REST Assured functional, and performance tests pass.
