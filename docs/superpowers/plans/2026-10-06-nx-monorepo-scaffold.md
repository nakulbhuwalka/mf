# Nx Monorepo Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the Nx workspace and five empty-but-buildable projects (fund-data, statement-parser, db/migrations, web, bdd) so that `pnpm nx run-many -t build test` passes.

**Architecture:** Nx is the top-level orchestrator with plain `nx:run-commands` targets that call `mvn`, `uv` and `pnpm`. There is no top-level Maven pom. Each project is a minimal skeleton with one passing test, with no business logic (that belongs to later sub-projects).

**Tech Stack:** Nx, pnpm, Maven (Java 21, Spring Boot), Liquibase (YAML), Python 3.12 + uv + FastAPI + pytest, React + TypeScript + Vite + Vitest, Cucumber (JVM and JS).

**Spec:** `docs/superpowers/specs/2026-10-06-mf-app-design.md`

## Global Constraints

- Build tools per CLAUDE.md: Maven for Java, uv for Python, pnpm for TypeScript/Node.
- Nx is the top-level build; no top-level Maven pom; Nx targets are plain `nx:run-commands`, no Nx plugins for Maven or Python.
- `fund-data` depends on `db/migrations` (Nx `implicitDependencies`).
- Liquibase changelogs are YAML (`db.changelog-master.yaml`); Spring auto-DDL is off.
- Services never call each other.
- Layout: `services/fund-data`, `services/statement-parser`, `db/migrations`, `web`, `bdd`.

## Review Focus

- Fresh clone with no build outputs: `pnpm install && pnpm nx run-many -t build test` works from scratch.
- `nx affected` and caching: changing only `web/` does not rebuild the Java projects.
- Missing tool (e.g. no `uv` or `mvn` on PATH): the target fails with the tool's own error instead of silently passing.
- `.gitignore` covers `target/`, `.venv/`, `node_modules/`, `.nx/`, `dist/`.
- Dependency order: `nx build fund-data` builds `db-migrations` first.

---

### Task 1: Nx workspace root

**Files:**
- Create: `package.json`, `pnpm-workspace.yaml`, `nx.json`
- Modify: `.gitignore`

**Interfaces:**
- Produces: a pnpm workspace with `nx` installed as a dev dependency. `nx.json` sets `targetDefaults` so `build` and `test` have `cache: true`, and `build` has `dependsOn: ["^build"]`. Later tasks add a `project.json` per project.

- [ ] **Step 1:** Run `pnpm init`, then `pnpm add -D -w nx`. In `pnpm-workspace.yaml` list `web` and `bdd/web` as packages.
- [ ] **Step 2:** Create `nx.json` with the target defaults above. Extend `.gitignore` with `node_modules/`, `.nx/`, `dist/`, `target/`, `.venv/`, `__pycache__/`.
- [ ] **Step 3: Verify.** Run `pnpm nx show projects`. Expected: exits 0 with an empty list.
- [ ] **Step 4: Commit** `chore: add Nx workspace root`.

### Task 2: db/migrations (Maven + Liquibase)

**Files:**
- Create: `db/migrations/pom.xml`, `db/migrations/project.json`, `db/migrations/src/main/resources/db/changelog/db.changelog-master.yaml`
- Test: `db/migrations/src/test/java/.../ChangelogParsesTest.java`

**Interfaces:**
- Produces: Nx project `db-migrations` with targets `build` (`mvn -q package`), `test` (`mvn -q test`) and `migrate` (`mvn liquibase:update`, run manually, not part of `build`). The artifact exposes `db/changelog/db.changelog-master.yaml` as a classpath resource; the master file is empty (an empty `databaseChangeLog: []`).

- [ ] **Step 1: Write the failing test** `ChangelogParsesTest.parsesMasterYaml`: loads the master changelog with Liquibase's `ChangeLogParserFactory` and asserts it has 0 change sets and does not throw.
- [ ] **Step 2:** Run `pnpm nx test db-migrations`. Expected: FAIL (no pom or no changelog).
- [ ] **Step 3:** Create the pom (Java 21, `liquibase-core` and `liquibase-maven-plugin`, JUnit 5, with the plugin's `changeLogFile` and the database URL as properties, default `jdbc:postgresql://localhost:5432/mf`) and the master YAML.
- [ ] **Step 4:** Run `pnpm nx test db-migrations`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: add db/migrations Liquibase module`.

### Task 3: services/fund-data (Java)

**Files:**
- Create: `services/fund-data/pom.xml`, `services/fund-data/project.json`, `src/main/java/.../FundDataApplication.java`, `src/main/resources/application.yaml`
- Test: `src/test/java/.../HealthTest.java`

**Interfaces:**
- Consumes: Nx project `db-migrations` (as `implicitDependencies`; the pom depends on its artifact).
- Produces: Nx project `fund-data` with `build`, `test` and `serve` (`mvn spring-boot:run`). The app exposes `GET /actuator/health` on port 8081. `application.yaml` sets `spring.jpa.hibernate.ddl-auto: none` and `spring.liquibase.enabled: false`.

- [ ] **Step 1: Write the failing test** `HealthTest.healthEndpointIsUp` (`@SpringBootTest` with a random port, an HTTP GET on `/actuator/health`, asserts status 200). The test must not need a database, so exclude the datasource auto-configuration in the test profile.
- [ ] **Step 2:** Run `pnpm nx test fund-data`. Expected: FAIL.
- [ ] **Step 3:** Create the pom (Spring Boot starter-web and actuator, test starter), the application class and `application.yaml`.
- [ ] **Step 4:** Run `pnpm nx test fund-data`. Expected: PASS, and `db-migrations` built first (visible in the Nx output).
- [ ] **Step 5: Commit** `feat: add fund-data service skeleton`.

### Task 4: services/statement-parser (Python + uv)

**Files:**
- Create: `services/statement-parser/pyproject.toml`, `project.json`, `src/statement_parser/main.py`
- Test: `services/statement-parser/tests/test_health.py`

**Interfaces:**
- Produces: Nx project `statement-parser` with `build` (`uv build`), `test` (`uv run pytest`) and `serve` (`uv run uvicorn statement_parser.main:app --port 8082`). `main.py` exposes `app: FastAPI` with `GET /health` returning `{"status": "ok"}`.

- [ ] **Step 1: Write the failing test** `test_health_returns_ok`: uses FastAPI `TestClient`, asserts status 200 and body `{"status": "ok"}`.
- [ ] **Step 2:** Run `pnpm nx test statement-parser`. Expected: FAIL (the import fails).
- [ ] **Step 3:** Create `pyproject.toml` (Python >=3.12; dependencies `fastapi`, `uvicorn`; dev group `pytest`, `httpx`) via `uv add`, and `main.py`.
- [ ] **Step 4:** Run `pnpm nx test statement-parser`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: add statement-parser service skeleton`.

### Task 5: web (React + Vite)

**Files:**
- Create: `web/package.json`, `web/project.json`, `web/vite.config.ts`, `web/src/App.tsx`, `web/src/main.tsx`, `web/index.html`
- Test: `web/src/App.test.tsx`

**Interfaces:**
- Produces: Nx project `web` with `build` (`pnpm vite build`), `test` (`pnpm vitest run`) and `serve` (`pnpm vite`). `App` renders a heading with the text "Mutual Funds".

- [ ] **Step 1: Write the failing test** `App.test.tsx`: renders `<App />` (Testing Library) and asserts `getByRole("heading", { name: "Mutual Funds" })` is present.
- [ ] **Step 2:** Run `pnpm nx test web`. Expected: FAIL.
- [ ] **Step 3:** Add dependencies (react, react-dom, vite, vitest, typescript, @testing-library/react, jsdom) with `pnpm add`, then create the Vite config and the app files.
- [ ] **Step 4:** Run `pnpm nx test web` and `pnpm nx build web`. Expected: both pass.
- [ ] **Step 5: Commit** `feat: add web app skeleton`.

### Task 6: bdd (Cucumber)

**Files:**
- Create: `bdd/services/pom.xml`, `bdd/services/project.json`, `bdd/services/src/test/resources/features/health.feature`, `bdd/services/src/test/java/.../HealthSteps.java`, `bdd/services/src/test/java/.../RunCucumberTest.java`, `bdd/web/package.json`, `bdd/web/project.json`, `bdd/web/features/home.feature`, `bdd/web/features/steps.ts`

**Interfaces:**
- Consumes: the running `fund-data` (`:8081`) and `statement-parser` (`:8082`) health endpoints.
- Produces: Nx projects `bdd-services` (target `test`: `mvn -q test`) and `bdd-web` (target `test`: `pnpm cucumber-js`). `implicitDependencies` on `fund-data`, `statement-parser` and `web`. Base URLs come from the env vars `FUND_DATA_URL` and `PARSER_URL`.

- [ ] **Step 1: Write the failing features.** `health.feature`: "Given the fund-data service is running / When I GET /actuator/health / Then the status is 200", plus the same for the parser's `/health`. `home.feature`: "When I open the home page / Then I see the heading 'Mutual Funds'".
- [ ] **Step 2:** Run `pnpm nx test bdd-services`. Expected: FAIL (steps undefined, or the connection is refused).
- [ ] **Step 3:** Implement the step definitions and the runner. The services suite starts and stops both services itself (`@BeforeAll` and `@AfterAll`, spawning `mvn spring-boot:run` and `uv run uvicorn`), so that `nx test bdd-services` is self-contained. The web suite uses Playwright against `vite preview`.
- [ ] **Step 4:** Run `pnpm nx run-many -t test`. Expected: all projects PASS.
- [ ] **Step 5: Commit** `feat: add Cucumber BDD suites`.

### Task 7: Whole-workspace verification

**Files:**
- Modify: `README.md` (create) with the commands in Step 1.

- [ ] **Step 1:** Create `README.md` documenting `pnpm install`, `pnpm nx run-many -t build test`, `pnpm nx serve <project>` and `pnpm nx migrate db-migrations`.
- [ ] **Step 2:** On a clean checkout (`git clean -xfd` after committing), run `pnpm install && pnpm nx run-many -t build test`. Expected: every project passes.
- [ ] **Step 3:** Run `pnpm nx affected -t build --base=HEAD~1` after touching only `web/src/App.tsx`. Expected: only `web` (and `bdd-web`) rebuild.
- [ ] **Step 4: Commit** `docs: add README with workspace commands`.
