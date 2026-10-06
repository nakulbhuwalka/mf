# Multi-Module MF Project: Design

Date: 2026-10-06

## Goal

A monorepo of independent services: a Java backend, a Python backend and a React frontend. The first scaffold is hello-world skeletons that prove the wiring end to end. It does not contain domain logic.

## Decisions

- **Coupling:** independent services. Each builds and runs on its own. They share only OpenAPI contracts.
- **Tooling (from CLAUDE.md):** Maven for Java, uv for Python, pnpm for TypeScript.
- **Root driver:** Docker Compose runs the full stack. A Makefile delegates build and test to each module's native tool.
- **Domain:** assumed to be mutual funds (continuing the earlier API). The scaffold uses neutral names ("funds", "statements") so the domain is easy to change.

## Layout

```
mf/
├── services/
│   ├── funds-api/         # Java 25, Spring Boot 4.1.1, Maven  (:8080)
│   └── statement-api/     # Python 3.13, FastAPI, uv           (:8000)
├── web/                   # React 19, Vite, TypeScript, pnpm   (:5173 dev, :80 in compose)
├── contracts/             # funds-api.openapi.yaml, statement-api.openapi.yaml
├── docker-compose.yml
├── Makefile
└── README.md
```

## Modules

### services/funds-api (Java)
- Spring Boot 4.1.1 on Java 25, built with Maven.
- `GET /health` returns `{"status":"ok"}`.
- `GET /api/funds` returns a small hard-coded list.
- One test for each endpoint. Dockerfile is multi-stage (Maven build, JRE runtime).

### services/statement-api (Python)
- FastAPI, managed with uv (`pyproject.toml` and `uv.lock`).
- `GET /health` returns `{"status":"ok"}`.
- `GET /api/statements` returns a small hard-coded payload.
- One pytest test for each endpoint. Dockerfile installs with uv.

### web (React)
- Vite, React 19 and TypeScript, managed with pnpm.
- One page that calls both services and shows each result, or an error state if a call fails.
- Dev: Vite proxies `/api/funds` to `:8080` and `/api/statements` to `:8000`, so there are no CORS problems.
- Compose: an nginx container serves the built app and proxies the same two paths to the service containers.
- One Vitest test for the page, with `fetch` mocked.

### contracts
- Hand-written OpenAPI files, one per service, describing `/health` and the sample endpoint.
- Nothing generates code from them yet. Client generation is a possible later step and is out of scope.

## Data flow

Browser → (Vite proxy or nginx) → `funds-api` or `statement-api`. The services never call each other.

## Error handling

- Services return JSON errors with the appropriate HTTP status (framework defaults, no custom error model yet).
- The frontend handles a failed or non-2xx call by showing an error message for that panel. One service being down does not break the other panel.

## Testing

- Java: `mvn test`. Python: `uv run pytest`. Web: `pnpm test`.
- `make test` runs all three. `make build` builds all three.
- Verification for the scaffold: all tests pass, and `docker compose up --build` serves a page that shows data from both services.

## Makefile targets

`build`, `test`, `up`, `down`. Each delegates to `mvn`, `uv`, `pnpm` or `docker compose`.

## Out of scope

Domain logic, databases, authentication, CI, generated API clients, service-to-service calls, production deployment.
