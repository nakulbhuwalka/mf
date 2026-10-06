# Database Design (fund-data, PostgreSQL)

Date: 2026-10-06
Status: **awaiting review**
Implements: `db/migrations` (Liquibase YAML) for the `fund-data` service. Parent spec: `2026-10-06-mf-app-design.md`.

Only public data is stored. Your portfolio never reaches this database.

## Assumptions about mfapi.in (verify before implementing)
- `GET /mf/latest` returns one object per scheme with the scheme code, name, fund house, type, category, both ISINs, and the latest NAV and its date.
- `GET /mf/{scheme_code}` returns scheme metadata plus the full NAV history as `{date: "dd-MM-yyyy", nav: "string"}` rows.
- A scheme can have two ISINs: growth/payout, and dividend reinvestment.

The first migration task should hit both endpoints once and confirm these field names.

## Entity overview
```
scheme 1 ──── * nav
scheme 1 ──── 1 scheme_sync
load_run   (standalone audit log of bulk loads)
```

## Tables

### scheme
One row per mutual fund scheme.

| column | type | constraints / notes |
|---|---|---|
| scheme_code | integer | PK. mfapi's scheme code, used as the natural key |
| scheme_name | text | NOT NULL |
| fund_house | text | |
| scheme_type | text | e.g. Open Ended |
| scheme_category | text | e.g. Equity Scheme - Large Cap Fund |
| isin_growth | varchar(12) | nullable |
| isin_div_reinvestment | varchar(12) | nullable |
| created_at | timestamptz | NOT NULL, default now() |
| updated_at | timestamptz | NOT NULL, default now() |

Indexes:
- `ix_scheme_isin_growth` on `isin_growth`.
- `ix_scheme_isin_div_reinvestment` on `isin_div_reinvestment`. Both support `GET /funds/by-isin/{isin}`, which checks either column.
- `ix_scheme_name_trgm`: GIN on `scheme_name gin_trgm_ops` (extension `pg_trgm`) for `GET /funds?q=` substring search.

### nav
One row per scheme per day.

| column | type | constraints / notes |
|---|---|---|
| scheme_code | integer | PK part 1, FK → scheme(scheme_code), ON DELETE CASCADE |
| nav_date | date | PK part 2 |
| nav | numeric(18,6) | NOT NULL. Exact decimal, never float |

The primary key `(scheme_code, nav_date)` serves range queries and makes upserts idempotent (`INSERT ... ON CONFLICT DO UPDATE`). Sizing: the whole fund universe is roughly 40k schemes, and a long history is a few thousand rows per scheme, so this is a few tens of millions of rows at the extreme. Histories are only loaded for schemes you actually request, so it stays much smaller.

### scheme_sync
Cache bookkeeping, kept apart from `scheme` so the catalogue stays a pure mirror of mfapi.

| column | type | constraints / notes |
|---|---|---|
| scheme_code | integer | PK, FK → scheme(scheme_code), ON DELETE CASCADE |
| latest_nav_date | date | newest NAV date known from the latest-NAV load; nullable |
| history_loaded | boolean | NOT NULL, default false. True once `/mf/{code}` was fetched at least once |
| last_checked_at | timestamptz | last time mfapi was contacted for this scheme; used for the refresh throttle |

Staleness rule (from the spec): a scheme is stale when the newest stored `nav.nav_date` is older than `scheme_sync.latest_nav_date`. The throttle skips a refetch if `last_checked_at` is within the configured window.

### load_run
Audit log for the initial and periodic `/mf/latest` bulk load.

| column | type | constraints / notes |
|---|---|---|
| id | bigint | PK, generated identity |
| started_at | timestamptz | NOT NULL |
| finished_at | timestamptz | nullable until finished |
| status | varchar(16) | NOT NULL: `RUNNING`, `SUCCESS`, `FAILED` (CHECK constraint) |
| schemes_upserted | integer | NOT NULL, default 0 |
| error | text | nullable |

## Key queries and how the schema serves them
| use | query shape | served by |
|---|---|---|
| Search | `scheme_name ILIKE '%q%'` ordered by name, with limit/offset | trigram GIN index |
| By ISIN | `isin_growth = :isin OR isin_div_reinvestment = :isin` | the two ISIN indexes |
| NAV range | `nav_date BETWEEN :from AND :to` for one scheme, ordered by date | nav PK |
| Staleness check | `max(nav_date)` for one scheme vs `scheme_sync.latest_nav_date` | nav PK (index-only backward scan) |

## Liquibase layout (YAML)
```
db/migrations/src/main/resources/db/changelog/
├── db.changelog-master.yaml        # includes the files below, in order
└── changes/
    ├── 001-extensions.yaml         # CREATE EXTENSION pg_trgm
    ├── 002-scheme.yaml             # scheme table + ISIN and name indexes
    ├── 003-nav.yaml                # nav table
    ├── 004-scheme-sync.yaml        # scheme_sync table
    └── 005-load-run.yaml           # load_run table
```
One change set per table, each with a rollback. Change set ids are `001`…`005`, author `mf`.

## Decisions to confirm
1. **Natural vs surrogate key:** `scheme_code` as PK, since mfapi's code is stable and it's the lookup key everywhere. (The alternative is a surrogate id plus a unique code, which adds a join for no benefit here.)
2. **No `fund_house` table:** kept as text on `scheme` (YAGNI). Normalize later if you want fund-house browsing.
3. **Two ISIN columns** rather than a separate `scheme_isin` table. Simple, and matches mfapi's shape.
4. **`pg_trgm` for search** rather than plain `LIKE` or full-text search. This requires the extension on the local Postgres (the standard `postgres` Docker image includes it).
5. **`load_run` table** is included for observability of the bulk load. Drop it if you'd rather keep the schema to three tables.
6. **No partitioning** of `nav`. It isn't needed at this size.
