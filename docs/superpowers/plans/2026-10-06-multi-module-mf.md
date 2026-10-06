# Multi-Module MF Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scaffold a monorepo with a Java service, a Python service and a React frontend that work together end to end under Docker Compose.

**Architecture:** Three independent modules, each built by its own native tool. They share only hand-written OpenAPI files in `contracts/`. The browser reaches both services through one origin: the Vite dev proxy in development and nginx in Compose. The services never call each other.

**Tech Stack:** Java 25, Spring Boot 4.1.1, Maven; Python 3.13, FastAPI, uv, pytest; React 19, Vite, TypeScript, pnpm, Vitest, Testing Library; Docker Compose, nginx.

**Spec:** `docs/superpowers/specs/2026-10-06-multi-module-mf-design.md`

## Global Constraints

- Java: Java 25, Spring Boot 4.1.1, built with Maven (plain `mvn`).
- Python: 3.13, FastAPI, managed with uv (`pyproject.toml` and `uv.lock`).
- Web: React 19, Vite, TypeScript, managed with pnpm.
- Layout: `services/funds-api` (:8080), `services/statement-api` (:8000), `web` (:5173 dev, :80 in compose), `contracts/`, `docker-compose.yml`, `Makefile`, `README.md`.
- Both services: `GET /health` returns `{"status":"ok"}`. Sample endpoints are `GET /api/funds` and `GET /api/statements`, each returning a small hard-coded payload.
- Services return JSON errors with the appropriate HTTP status (framework defaults, no custom error model).
- Frontend: a failed or non-2xx call shows an error message for that panel only. One service being down does not break the other panel.
- Dev proxy and nginx forward `/api/funds` to `funds-api` and `/api/statements` to `statement-api`. The path is not rewritten.
- Makefile targets: `build`, `test`, `up`, `down`. Each delegates to `mvn`, `uv`, `pnpm` or `docker compose`.
- Out of scope: domain logic, databases, auth, CI, generated API clients, service-to-service calls, production deployment.

## Review Focus

1. Unknown route on either service: expect a JSON 404, not a crash or an HTML page. Pinned in Tasks 1 and 2.
2. A service returns a non-2xx status: that panel shows its error and the other panel still shows data. Pinned in Task 4.
3. `fetch` rejects (network failure, service down): same behavior as a non-2xx. Pinned in Task 4.
4. A service returns an empty list or empty object: the panel renders without crashing and does not show an error. Pinned in Task 4.
5. Proxy path handling: a request to `/api/funds` through nginx reaches `/api/funds` on the service, not `/` or `/funds`. Pinned in Task 5.

---

### Task 1: funds-api (Java)

**Files:**
- Create: `services/funds-api/pom.xml`, `services/funds-api/src/main/java/com/mf/funds/FundsApplication.java`, `HealthController.java`, `FundController.java` (same package)
- Test: `services/funds-api/src/test/java/com/mf/funds/FundsApiTests.java`
- Create: `.gitignore` at the repo root (`target/`, `.venv/`, `node_modules/`, `dist/`, `__pycache__/`, `.pytest_cache/`)

**Interfaces:**
- Produces: `GET /health` → `{"status":"ok"}`. `GET /api/funds` → `[{"id":"F001","name":"Sample Equity Fund"},{"id":"F002","name":"Sample Debt Fund"}]`. Listens on port 8080 (Spring default).

- [ ] **Step 1: Confirm the toolchain.** Run `java -version` and `mvn -version`. Expected: Java 25 is the active JDK. If not, stop and report.
- [ ] **Step 2: Create `pom.xml`.** Parent `spring-boot-starter-parent` 4.1.1, `java.version` 25, dependencies `spring-boot-starter-web` (use the Boot 4 starter name for MVC if it differs) and `spring-boot-starter-test`. Group `com.mf`, artifact `funds-api`. Add `FundsApplication` with `@SpringBootApplication` and a `main`.
- [ ] **Step 3: Write the failing tests** in `FundsApiTests` using MockMvc (Boot 4 moved the MockMvc test packages; use the 4.1.1 locations):
  - `healthReturnsOk`: GET `/health` → 200, `$.status` equals `"ok"`.
  - `fundsReturnsSampleList`: GET `/api/funds` → 200, `$.length()` equals 2, `$[0].id` equals `"F001"`, `$[0].name` equals `"Sample Equity Fund"`.
  - `unknownRouteReturnsJson404`: GET `/nope` → 404 and content type compatible with `application/json`.
- [ ] **Step 4: Run** `mvn -f services/funds-api test`. Expected: the first two FAIL (404), the third may already pass.
- [ ] **Step 5: Implement** `HealthController` (`GET /health`, returns `Map<String,String>` with `status` = `ok`; do not use Actuator, which reports `UP`) and `FundController` (`GET /api/funds`, returns `List<Fund>` where `record Fund(String id, String name)` is nested in the controller).
- [ ] **Step 6: Run** `mvn -f services/funds-api test`. Expected: 3 tests PASS.
- [ ] **Step 7: Commit** `.gitignore` and `services/funds-api` with message `feat: add funds-api service`.

### Task 2: statement-api (Python)

**Files:**
- Create: `services/statement-api/pyproject.toml`, `uv.lock`, `app/__init__.py`, `app/main.py`
- Test: `services/statement-api/tests/test_main.py`

**Interfaces:**
- Produces: `app.main:app` (a FastAPI instance). `GET /health` → `{"status":"ok"}`. `GET /api/statements` → `{"statements":[{"id":"S001","fundId":"F001","period":"2026-09"}]}`. Served on port 8000.

- [ ] **Step 1: Create the project.** Run `uv init --app --python 3.13 services/statement-api`, remove any generated `main.py`/`hello.py`, then `uv add --directory services/statement-api fastapi "uvicorn[standard]"` and `uv add --directory services/statement-api --dev pytest httpx`.
- [ ] **Step 2: Write the failing tests** in `tests/test_main.py` using `fastapi.testclient.TestClient(app)`:
  - `test_health`: GET `/health` → 200, body `{"status": "ok"}`.
  - `test_statements`: GET `/api/statements` → 200, `body["statements"][0]["id"] == "S001"`, `["fundId"] == "F001"`, `["period"] == "2026-09"`.
  - `test_unknown_route_is_json_404`: GET `/nope` → 404, `response.json()` contains `"detail"`.
- [ ] **Step 3: Run** `uv run --directory services/statement-api pytest`. Expected: FAIL with an import error for `app.main`.
- [ ] **Step 4: Implement** `app/main.py`: `app = FastAPI()` with `health() -> dict[str, str]` and `statements() -> dict`, routed as above.
- [ ] **Step 5: Run** the same pytest command. Expected: 3 PASS.
- [ ] **Step 6: Commit** `services/statement-api` with message `feat: add statement-api service`.

### Task 3: OpenAPI contracts

**Files:**
- Create: `contracts/funds-api.openapi.yaml`, `contracts/statement-api.openapi.yaml`

**Interfaces:**
- Consumes: the response shapes pinned in Tasks 1 and 2.

- [ ] **Step 1: Write both files** (OpenAPI 3.1). Each documents `GET /health` and its sample endpoint with a 200 response schema that matches the payload exactly.
- [ ] **Step 2: Validate.** Run `pnpm dlx @redocly/cli lint contracts/*.yaml`. Expected: no errors.
- [ ] **Step 3: Commit** `contracts/` with message `docs: add OpenAPI contracts`.

### Task 4: web (React)

**Files:**
- Create: `web/` via `pnpm create vite@latest web --template react-ts`, then add `web/src/Panel.tsx` and `web/src/api.ts`, and modify `web/src/App.tsx`, `web/vite.config.ts`
- Test: `web/src/App.test.tsx`

**Interfaces:**
- Produces: `fetchJson(url: string): Promise<unknown>` in `api.ts`. It rejects with an `Error` when `fetch` rejects or the response is not 2xx.
- Produces: `Panel({ title, url }: { title: string; url: string })` in `Panel.tsx`. It shows "Loading…" first, then the pretty-printed JSON in a `<pre>`, or an element with `role="alert"` and the text `Failed to load <title>` on failure.
- Produces: `App` renders `<Panel title="Funds" url="/api/funds" />` and `<Panel title="Statements" url="/api/statements" />`.

- [ ] **Step 1: Scaffold and add test tooling.** Run the Vite command above (React 19), then `pnpm --dir web add -D vitest jsdom @testing-library/react @testing-library/jest-dom`. Configure Vitest in `vite.config.ts` with `environment: "jsdom"`. Add the script `"test": "vitest run"`.
- [ ] **Step 2: Add the dev proxy** in `vite.config.ts`: `/api/funds` → `http://localhost:8080` and `/api/statements` → `http://localhost:8000`, with no rewrite.
- [ ] **Step 3: Write the failing tests** in `App.test.tsx`. Mock `globalThis.fetch` per URL:
  - `shows data from both services`: funds → `[{"id":"F001","name":"Sample Equity Fund"}]`, statements → `{"statements":[{"id":"S001"}]}`. Assert `Sample Equity Fund` and `S001` appear.
  - `non-2xx on one service only fails that panel`: funds → 500, statements → 200. Assert the alert `Failed to load Funds` and that the statements data is still shown.
  - `rejected fetch only fails that panel`: statements `fetch` rejects with `TypeError`. Assert `Failed to load Statements` and that the funds data is still shown.
  - `empty responses render without error`: funds → `[]`, statements → `{}`. Assert no `role="alert"` and no thrown error.
- [ ] **Step 4: Run** `pnpm --dir web test`. Expected: FAIL (components missing).
- [ ] **Step 5: Implement** `api.ts`, `Panel.tsx` and `App.tsx` per the Interfaces block. Delete the Vite template demo content and CSS that `App` no longer uses.
- [ ] **Step 6: Run** `pnpm --dir web test`. Expected: 4 PASS. Then run `pnpm --dir web build`. Expected: succeeds with no type errors.
- [ ] **Step 7: Commit** `web/` with message `feat: add React frontend`.

### Task 5: Docker and Compose

**Files:**
- Create: `services/funds-api/Dockerfile`, `services/statement-api/Dockerfile`, `web/Dockerfile`, `web/nginx.conf`, `docker-compose.yml`, plus a `.dockerignore` in each module

**Interfaces:**
- Consumes: the ports and paths pinned in Tasks 1, 2 and 4.
- Produces: Compose services `funds-api`, `statement-api` and `web`. `web` publishes host port 80.

- [ ] **Step 1: `funds-api/Dockerfile`.** Multi-stage: a Maven + JDK 25 stage runs `mvn -B package -DskipTests`, and a JRE 25 stage runs the jar. It exposes 8080.
- [ ] **Step 2: `statement-api/Dockerfile`.** Base `python:3.13-slim`, copy `uv` from `ghcr.io/astral-sh/uv`, run `uv sync --frozen --no-dev`, and start `uv run uvicorn app.main:app --host 0.0.0.0 --port 8000`.
- [ ] **Step 3: `web/Dockerfile` and `nginx.conf`.** Stage 1 builds with pnpm (`corepack enable`). Stage 2 is nginx serving `dist/`. `nginx.conf` has `location /api/funds { proxy_pass http://funds-api:8080; }` and `location /api/statements { proxy_pass http://statement-api:8000; }`. Use `proxy_pass` with no URI part so the path is preserved. Add an SPA fallback to `index.html` for `/`.
- [ ] **Step 4: `docker-compose.yml`.** Three services built from the module folders. `web` maps `80:80` and has `depends_on` for both services.
- [ ] **Step 5: Verify the stack.** Run `docker compose up --build -d`, then:
  - `curl -s localhost/api/funds` returns the funds JSON (pins that nginx preserves the path).
  - `curl -s localhost/api/statements` returns the statements JSON.
  - `curl -s -o /dev/null -w "%{http_code}" localhost/` returns 200.
  - `docker compose stop statement-api`, then `curl -s -o /dev/null -w "%{http_code}" localhost/api/statements` returns 502 and `curl -s localhost/api/funds` still returns data.
  - Run `docker compose down`.
- [ ] **Step 6: Commit** with message `feat: add Dockerfiles and compose stack`.

### Task 6: Makefile and README

**Files:**
- Create: `Makefile`, `README.md`

**Interfaces:**
- Consumes: the module commands used in Tasks 1, 2, 4 and 5.

- [ ] **Step 1: Write the `Makefile`** with `.PHONY` targets:
  - `build`: `mvn -f services/funds-api -B package -DskipTests`; `uv --directory services/statement-api sync`; `pnpm --dir web install --frozen-lockfile && pnpm --dir web build`.
  - `test`: `mvn -f services/funds-api -B test`; `uv run --directory services/statement-api pytest`; `pnpm --dir web test`.
  - `up`: `docker compose up --build`. `down`: `docker compose down`.
- [ ] **Step 2: Write `README.md`.** State the layout, the prerequisites (JDK 25, Maven, uv, pnpm, Docker), the four make targets, how to run each module in development (ports and the Vite proxy), and where the contracts live.
- [ ] **Step 3: Verify.** Run `make build` and `make test`. Expected: both exit 0, with 3 Java, 3 Python and 4 web tests passing.
- [ ] **Step 4: Commit** with message `chore: add Makefile and README`.
