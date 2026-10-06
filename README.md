# mf

Personal mutual fund app: view and analyze your own funds (from a CAMS CAS statement), analyze any fund from mfapi.in, and compare fund performance.

Design: [docs/superpowers/specs/2026-10-06-mf-app-design.md](docs/superpowers/specs/2026-10-06-mf-app-design.md) ·
Database: [docs/superpowers/specs/2026-10-06-database-design.md](docs/superpowers/specs/2026-10-06-database-design.md)

## Layout

| Project | Path | Stack |
|---|---|---|
| `fund-data` | `services/fund-data` | Java 25, Spring Boot, Maven (OpenAPI: `openapi.yaml`) |
| `statement-parser` | `services/statement-parser` | Python 3.12, uv, FastAPI (OpenAPI: `openapi.yaml`) |
| `db-migrations` | `db/migrations` | Maven, Liquibase (YAML), PostgreSQL |
| `web` | `web` | React, TypeScript, Vite, pnpm |
| `bdd-services` | `bdd/services` | Cucumber-JVM, black-box over HTTP |
| `bdd-web` | `bdd/web` | cucumber-js, Playwright (installed Google Chrome) |

Nx is the top-level build. Targets are plain `nx:run-commands` wrappers around `mvn`, `uv` and `pnpm`; there is no top-level Maven pom.

## Prerequisites

Node 24 with pnpm, JDK 25, Maven, uv, Google Chrome (for `bdd-web`), and Docker (the `db-migrations` tests start a throwaway PostgreSQL with Testcontainers; they fail, not skip, when Docker is unavailable).

## Commands

```sh
pnpm install                          # once
pnpm nx run-many -t build test        # build and test everything
pnpm nx serve fund-data               # http://localhost:8081  (/actuator/health)
pnpm nx serve statement-parser        # http://localhost:8082  (/health)
pnpm nx serve web                     # http://localhost:5173
pnpm nx run db-migrations:db-up       # local PostgreSQL 18 in Docker (db/docker-compose.yml), localhost:5432, db/user/password mf
pnpm nx run db-migrations:migrate     # apply the Liquibase changelogs to it (not `nx migrate`, which is Nx's own upgrade command)
pnpm nx run db-migrations:db-down     # stop it; the data volume is kept (`docker compose -f db/docker-compose.yml down -v` wipes it)
pnpm nx affected -t build test        # only what changed
```

`fund-data` builds `db-migrations` first (`mvn install` puts its artifact in `~/.m2`). The BDD suites start the services themselves, from the freshly built artifacts. If something already answers on 8081, 8082 or 4173 they refuse to run, rather than silently testing a stale process. To test an already-running instance on purpose, set `FUND_DATA_URL`, `PARSER_URL` or `WEB_URL`; the suite then starts nothing.
