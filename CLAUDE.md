# CLAUDE.md — Live Auction Platform

Real-time auction platform (Java 21 / Spring Boot 3). Portfolio project meant to impress backend recruiters:
it must be **deployed, interactive, tested for concurrency, and well documented in English**.

Full specification: see `SPEC.md`. Always read it before starting a phase.

## Working rules
- Work **one phase at a time** (see SPEC.md §18). Do not start the next phase until the Definition of Done of the current one is met.
- Before writing code for a phase: briefly state the plan. After: run the build and tests, report results.
- Never commit secrets. Use environment variables + `.env.example`.
- All code, comments, commit messages, API docs and README are in **English**.
- Commit style: Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`). Small, focused commits.
- Money is **always** `long` cents (never `double`/`float`). Timestamps are UTC `Instant`; the **server clock is the only clock**.
- Postgres is the source of truth. Redis is a performance/atomicity layer and must be rebuildable from Postgres.
- Every new behavior needs a test. Concurrency-sensitive code needs a concurrency test.
- Prefer constructor injection, records for DTOs, no Lombok magic beyond `@Slf4j`/`@RequiredArgsConstructor` (optional).
- Package by feature (`auction`, `bid`, `user`, `realtime`, `outbox`, `security`, `common`), not by layer.

## Commands
- Infra: `docker compose up -d postgres redis rabbitmq`
- Backend: `cd backend && ./mvnw spring-boot:run`
- Tests: `cd backend && ./mvnw verify` (uses Testcontainers; Docker must be running)
- Full stack: `docker compose up --build`
- Bot: `docker compose --profile bot up bot`

## Definition of "done" for any task
1. Compiles, `./mvnw verify` green.
2. New/changed behavior covered by tests.
3. OpenAPI/Swagger reflects the change.
4. README/SPEC updated if behavior or setup changed.
