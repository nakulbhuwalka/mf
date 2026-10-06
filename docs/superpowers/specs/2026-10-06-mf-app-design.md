# Mutual Fund App: Design

Date: 2026-10-06

## Purpose
A personal, single-user, locally run app to view and analyze mutual funds. It is also a learning project for the superpowers workflow. There is no auth.

**Success:** upload a CAMS consolidated statement (CAS) PDF and see your holdings and returns; browse any fund's NAV history; overlay several funds to compare performance.

**Assumptions:** single user, local only. "Analyze" means returns (CAGR, XIRR on holdings) and NAV charts.

## Architecture (direct calls, client-side analytics)
```
mf/
├── nx.json, package.json, pnpm-workspace.yaml   # Nx top-level build; plain run-commands targets (mvn / uv / pnpm)
├── services/
│   ├── fund-data/            # Java, Spring Boot, own pom.xml
│   └── statement-parser/     # Python, uv, FastAPI, stateless
├── db/migrations/            # Maven + Liquibase (YAML changelogs), PostgreSQL
├── web/                      # React + TypeScript + Vite, pnpm
└── bdd/                      # Cucumber BDD
```
There is no top-level Maven pom. Each Java module keeps its own `pom.xml`, and Nx orchestrates builds and task dependencies (`fund-data` depends on `db/migrations`). Services never call each other; only the frontend joins their data.

## Components

### fund-data (Java)
- **Initial load:** calls `https://api.mfapi.in/mf/latest` and upserts all schemes with their latest NAV and ISIN.
- **On request** for a fund: if the newest stored NAV is stale, fetch `https://api.mfapi.in/mf/{scheme_code}` and upsert the full history, otherwise serve from the database. "Stale" means the stored latest NAV date is older than the NAV date reported by `/mf/latest` for that scheme. A short per-scheme refresh throttle prevents repeated mfapi calls.
- **Endpoints:** `GET /funds?q=`, `GET /funds/{code}/nav?from=&to=`, `GET /funds/by-isin/{isin}`.
- **Failure:** if mfapi is unreachable, serve cached data with `stale: true`.
- The schema is owned by Liquibase, and Spring auto-DDL is off.

### db/migrations (Maven + Liquibase)
`db.changelog-master.yaml` plus per-change YAML files, applied to PostgreSQL (Docker Compose for local development) with `liquibase-maven-plugin`. It is run explicitly, not on service startup.

### statement-parser (Python)
`POST /parse` takes a CAS PDF (multipart, optional password) and returns normalized JSON: folios, schemes (with ISIN), transactions and closing units. It is stateless: the PDF exists in memory for the request only. Errors: 422 with a reason for a bad PDF or wrong password; a partial parse returns the transactions it found plus `warnings`, and nothing is dropped silently.

### web (React)
- Calls both services directly (CORS enabled on both for the dev origin).
- The parsed portfolio lives in browser storage only.
- XIRR, CAGR and comparison are computed client-side. Comparison rebases each fund's NAV series to 100 at a common start date and overlays them; it also shows CAGR over 1, 3 and 5 years.
- Scheme matching: parsed ISIN to `GET /funds/by-isin/{isin}`. If there is no match, the UI shows "unmatched" and lets the user pick a fund manually.

## Data flows
1. **Explore a fund:** web → fund-data search and NAV → chart.
2. **Own portfolio:** web → parser `/parse` → browser storage; web fetches NAV per scheme from fund-data and computes value and returns.
3. **Compare:** web fetches N NAV series, rebases and overlays them.

## Testing
- Java: JUnit, mfapi mocked with WireMock, Testcontainers PostgreSQL.
- Python: pytest with synthetic CAS fixtures (never real statements).
- Web: Vitest, with XIRR and CAGR checked against known values.
- BDD (`bdd/`): Cucumber-JVM runs black-box over HTTP against both services (`nx run bdd:services`); cucumber-js with Playwright drives the React app against stubbed backends (`nx run bdd:web`).

## Delivery order (each its own spec → plan → build)
1. Nx monorepo scaffold
2. db/migrations
3. fund-data
4. statement-parser
5. web
6. bdd
