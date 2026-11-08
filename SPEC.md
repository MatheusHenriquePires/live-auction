# SPEC — Live Auction Platform

## 1. Goal
A real-time auction system where many users bid simultaneously, everyone sees bids instantly, and the system stays **correct under concurrency**. Showcases: Spring Boot, WebSocket/STOMP, Redis atomicity (Lua), PostgreSQL, RabbitMQ, transactional outbox, idempotency, JWT, Testcontainers, CI/CD, Docker.

Non-goals: payments, shipping, image upload (use image URLs), multi-currency.

## 2. Tech stack
| Area | Choice |
|---|---|
| Language/Runtime | Java 21, Maven wrapper |
| Framework | Spring Boot 3.3+ (Web, Validation, Security, Data JPA, WebSocket, AMQP, Data Redis, Actuator) |
| DB | PostgreSQL 16 + Flyway |
| Cache/Atomicity | Redis 7 (Lua scripts, pub/sub for cross-instance fan-out) |
| Broker | RabbitMQ 3 (management image) |
| Auth | Spring Security + JWT (access token 15 min, refresh token 7 days) |
| Scheduling | Spring `@Scheduled` + ShedLock (JDBC provider) |
| Docs | springdoc-openapi (Swagger UI at `/swagger-ui.html`) |
| Mapping | MapStruct (optional) |
| Tests | JUnit 5, Mockito, AssertJ, Testcontainers (Postgres, Redis, RabbitMQ), Awaitility, Spring `WebSocketStompClient` |
| Observability | Actuator + Micrometer (Prometheus endpoint), structured logs with correlation id |
| Frontend | React + Vite + TypeScript + `@stomp/stompjs` (simple, clean UI) |
| Bot | Separate small Spring Boot module (or Node script) simulating bidders |
| Load test | k6 |
| CI/CD | GitHub Actions |
| Deploy | Backend + infra on a VPS / Railway / Render (docker compose), frontend on Vercel |

## 3. Repository layout
```
live-auction/
├─ CLAUDE.md
├─ SPEC.md
├─ README.md                  (English, with diagram, GIF, badges)
├─ docker-compose.yml
├─ docker-compose.prod.yml
├─ .env.example
├─ .github/workflows/ci.yml
├─ docs/
│  ├─ architecture.md         (mermaid diagrams)
│  ├─ adr/                    (architecture decision records)
│  └─ load-test/              (k6 script + results)
├─ backend/
│  ├─ pom.xml
│  └─ src/main/java/com/<you>/auction/
│     ├─ AuctionApplication.java
│     ├─ common/              (errors, config, clock, id generation)
│     ├─ security/            (JWT filter, config, auth controller)
│     ├─ user/
│     ├─ auction/             (entity, repo, service, controller, scheduler)
│     ├─ bid/                 (entity, repo, BidService, RedisBidGate, controller)
│     ├─ realtime/            (STOMP config, publisher, subscribers)
│     └─ outbox/              (entity, relay, RabbitMQ publisher/consumers)
│     src/main/resources/{application.yml, db/migration, redis/place_bid.lua}
│     src/test/java/...
├─ frontend/
├─ bot/
└─ load-test/
```

## 4. Docker Compose
Services: `postgres`, `redis`, `rabbitmq`, `backend`, `frontend` (nginx), `bot` (profile `bot`).
- Healthchecks on postgres/redis/rabbitmq; `backend` uses `depends_on: condition: service_healthy`.
- Backend multi-stage Dockerfile (build with Maven, run on `eclipse-temurin:21-jre`, non-root user).
- Env via `.env` (provide `.env.example`): DB creds, JWT secret, Redis/Rabbit hosts.
- Dev profile exposes ports: 5432, 6379, 5672, 15672, 8080, 5173.

## 5. Data model (Flyway)

`V1__init.sql`
```sql
CREATE TABLE users (
  id            UUID PRIMARY KEY,
  email         VARCHAR(255) NOT NULL UNIQUE,
  display_name  VARCHAR(60)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role          VARCHAR(20)  NOT NULL DEFAULT 'USER',   -- USER | ADMIN | BOT
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE auctions (
  id               UUID PRIMARY KEY,
  seller_id        UUID NOT NULL REFERENCES users(id),
  title            VARCHAR(140) NOT NULL,
  description      TEXT,
  image_url        VARCHAR(500),
  starting_price   BIGINT NOT NULL CHECK (starting_price > 0),   -- cents
  min_increment    BIGINT NOT NULL CHECK (min_increment > 0),    -- cents
  current_price    BIGINT NOT NULL,                              -- cents
  leader_id        UUID REFERENCES users(id),
  bid_count        INT  NOT NULL DEFAULT 0,
  status           VARCHAR(20) NOT NULL,   -- SCHEDULED | ACTIVE | ENDED | CANCELLED
  starts_at        TIMESTAMPTZ NOT NULL,
  ends_at          TIMESTAMPTZ NOT NULL,
  winner_id        UUID REFERENCES users(id),
  version          BIGINT NOT NULL DEFAULT 0,                    -- optimistic lock
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (ends_at > starts_at)
);
CREATE INDEX idx_auctions_status_ends ON auctions(status, ends_at);

CREATE TABLE bids (
  id          UUID PRIMARY KEY,              -- = client-provided bidId (idempotency key)
  auction_id  UUID NOT NULL REFERENCES auctions(id),
  bidder_id   UUID NOT NULL REFERENCES users(id),
  amount      BIGINT NOT NULL CHECK (amount > 0),
  placed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (auction_id, amount)                -- last-resort barrier against equal bids
);
CREATE INDEX idx_bids_auction_amount ON bids(auction_id, amount DESC);

CREATE TABLE outbox_events (
  id             UUID PRIMARY KEY,
  aggregate_type VARCHAR(40)  NOT NULL,      -- AUCTION
  aggregate_id   UUID         NOT NULL,
  event_type     VARCHAR(60)  NOT NULL,      -- BidPlaced | AuctionExtended | AuctionEnded | AuctionStarted
  payload        JSONB        NOT NULL,
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  published_at   TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_events(created_at) WHERE published_at IS NULL;

CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id),
  token_hash VARCHAR(100) NOT NULL UNIQUE, expires_at TIMESTAMPTZ NOT NULL, revoked BOOLEAN NOT NULL DEFAULT false
);

-- ShedLock
CREATE TABLE shedlock (
  name VARCHAR(64) PRIMARY KEY, lock_until TIMESTAMP NOT NULL,
  locked_at TIMESTAMP NOT NULL, locked_by VARCHAR(255) NOT NULL
);
```
Seed (`V2__seed.sql` or dev-only runner): 1 admin, 5 bot users, 8 sample auctions with varied end times.

## 6. Domain rules
- Status lifecycle: `SCHEDULED → ACTIVE → ENDED` (or `CANCELLED` only if `bid_count = 0`).
- A bid is valid only if: auction `ACTIVE`, `now < ends_at` (server clock), bidder ≠ seller, bidder ≠ current leader, and
  - first bid: `amount >= starting_price`
  - later bids: `amount >= current_price + min_increment`
- **Anti-sniping**: if `ends_at - now <= 30s` when a valid bid lands, `ends_at += 30s` (constants configurable: `auction.antisnipe.window-seconds=30`, `extension-seconds=30`, `max-extensions=20`).
- On end: `winner_id = leader_id` (null if no bids), emit `AuctionEnded`.
- Amounts in cents. Clients format currency.

## 7. REST API (`/api/v1`)
Auth
- `POST /auth/register` `{email, displayName, password}` → 201
- `POST /auth/login` → `{accessToken, refreshToken, expiresIn}`
- `POST /auth/refresh`, `POST /auth/logout`
- `GET /users/me`

Auctions
- `GET /auctions?status=&page=&size=&sort=` (public) → paged summaries
- `GET /auctions/{id}` (public) → detail + `serverTime` + last 20 bids
- `POST /auctions` (auth) → create (`startsAt` may be now)
- `POST /auctions/{id}/cancel` (seller/admin, only if no bids)
- `GET /auctions/{id}/bids?page=&size=`

Bids
- `POST /auctions/{id}/bids` (auth) body `{bidId: UUID, amount: long}` → 201 `BidResponse` (REST fallback, same logic as WebSocket)

Ops
- `GET /actuator/health`, `/actuator/prometheus`
- `GET /time` → `{serverTime}` for client clock-offset calculation

### Error format (RFC 7807 `application/problem+json`)
`{type, title, status, detail, code, traceId}`. Domain codes: `AUCTION_NOT_ACTIVE`, `AUCTION_ENDED`, `BID_TOO_LOW` (include `minimumAmount`), `ALREADY_LEADER`, `SELLER_CANNOT_BID`, `DUPLICATE_BID` (returns original result, HTTP 200), `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`.

## 8. WebSocket / STOMP protocol
- Endpoint: `/ws` (SockJS fallback optional). Auth: JWT sent in STOMP `CONNECT` header `Authorization: Bearer ...`, validated by a `ChannelInterceptor`. Anonymous users may **subscribe** to public topics but not **send**.
- Client → server: `SEND /app/auctions/{id}/bid` payload `{bidId, amount}`
- Server → client topics:
  - `/topic/auctions/{id}` — public events (below)
  - `/user/queue/bid-results` — private ack/error for the sender
  - `/topic/auctions` — lobby updates (price/status/time changes of any auction)

Event payloads (all include `type`, `auctionId`, `serverTime`, `seq`):
```json
{"type":"BID_PLACED","auctionId":"…","amount":12500,"bidderDisplayName":"Ana","bidCount":7,"endsAt":"2026-…Z","seq":42}
{"type":"AUCTION_EXTENDED","auctionId":"…","endsAt":"…","seq":43}
{"type":"AUCTION_ENDED","auctionId":"…","winnerDisplayName":"Ana","finalPrice":12500,"seq":44}
```
- `seq` is a per-auction monotonic number (from the DB `bid_count`/event counter) so clients detect gaps and re-fetch `GET /auctions/{id}` after reconnect.
- Private result: `{"type":"BID_ACCEPTED"|"BID_REJECTED","bidId":"…","code":"BID_TOO_LOW","minimumAmount":13000}`.

## 9. Bid placement flow (core)
`BidService.placeBid(auctionId, bidderId, bidId, amount)`:
1. **Idempotency check**: if `bids.id = bidId` exists → return stored result (`DUPLICATE_BID`, no side effects).
2. **Redis gate** (`RedisBidGate`) runs `place_bid.lua` atomically (see below). On reject → map to domain error immediately (cheap, no DB hit).
3. On `OK`: open DB transaction:
   - `SELECT … FROM auctions WHERE id=? ` with `@Version` check; update `current_price`, `leader_id`, `bid_count`, `ends_at`.
   - insert `bids` row (PK = `bidId`; unique `(auction_id, amount)`).
   - insert `outbox_events` row(s): `BidPlaced` (+ `AuctionExtended` if extended).
   - commit.
4. **If the DB transaction fails** after Redis accepted: run compensation → rebuild Redis state for that auction from Postgres (`AuctionStateLoader.reload(id)`), return 409/500 to the client. Log at WARN with traceId.
5. After commit (use `TransactionSynchronization.afterCommit` or the outbox relay): broadcast to `/topic/auctions/{id}`, `/topic/auctions`, and the sender's private queue.

Redis keys (hash tag keeps keys in one slot if clustered later):
- `auction:{<id>}:state` hash → `status, current_price, leader_id, min_increment, starting_price, bid_count, ends_at_ms, extensions`
- `bid:{<id>}:idem:<bidId>` string, TTL 24h
State is loaded when an auction becomes ACTIVE and on app start for all ACTIVE auctions; `reload` helper rebuilds from Postgres.

### `redis/place_bid.lua`
```lua
-- KEYS[1]=state hash, KEYS[2]=idempotency key
-- ARGV: bidderId, amount, nowMs, windowMs, extensionMs, maxExtensions, sellerId
if redis.call('EXISTS', KEYS[2]) == 1 then return {'DUPLICATE'} end
local s = redis.call('HMGET', KEYS[1],
  'status','current_price','leader_id','min_increment','starting_price','bid_count','ends_at_ms','extensions')
if not s[1] or s[1] ~= 'ACTIVE' then return {'NOT_ACTIVE'} end
local bidder, amount, now = ARGV[1], tonumber(ARGV[2]), tonumber(ARGV[3])
local endsAt = tonumber(s[7])
if now >= endsAt then return {'ENDED'} end
if bidder == ARGV[7] then return {'SELLER_CANNOT_BID'} end
if s[3] == bidder then return {'ALREADY_LEADER'} end
local count = tonumber(s[6])
local minimum
if count == 0 then minimum = tonumber(s[5]) else minimum = tonumber(s[2]) + tonumber(s[4]) end
if amount < minimum then return {'BID_TOO_LOW', tostring(minimum)} end
local extended, ext = 0, tonumber(s[8])
if endsAt - now <= tonumber(ARGV[4]) and ext < tonumber(ARGV[6]) then
  endsAt = endsAt + tonumber(ARGV[5]); ext = ext + 1; extended = 1
end
redis.call('HSET', KEYS[1], 'current_price', amount, 'leader_id', bidder,
  'bid_count', count + 1, 'ends_at_ms', endsAt, 'extensions', ext)
redis.call('SET', KEYS[2], '1', 'EX', 86400)
return {'OK', tostring(amount), tostring(endsAt), tostring(extended), tostring(count + 1)}
```
Test the script directly (Testcontainers Redis) with table-driven cases.

## 10. Auction lifecycle scheduler
- `AuctionActivationJob` (every 1s, ShedLock `lockAtMostFor=10s`): `SCHEDULED` with `starts_at <= now` → `ACTIVE`, load Redis state, emit `AuctionStarted`.
- `AuctionClosingJob` (every 1s, ShedLock): `ACTIVE` with `ends_at <= now` → in one transaction set `ENDED`, `winner_id`, write `AuctionEnded` to outbox; set Redis `status=ENDED`. Re-read `ends_at` from DB inside the transaction (it may have been extended).
- Idempotent: running twice must not double-emit (guard with status check + optimistic lock).

## 11. Outbox + RabbitMQ
- `OutboxRelay` (`@Scheduled` every 200ms, ShedLock): select up to 100 unpublished rows `FOR UPDATE SKIP LOCKED`, publish to exchange `auction.events` (topic) with routing key `auction.<eventType>`, mark `published_at`. At-least-once delivery → consumers must be idempotent (dedupe by event id).
- Consumers (queues bound to `auction.#`):
  - `realtime-fanout`: broadcast via STOMP (this is how multiple backend instances all push to their own WebSocket clients).
  - `notifications`: logs/fake email "You were outbid" / "You won" (pluggable interface `Notifier`).
  - `history-projection`: optional read model for stats (bids per hour).
- Dead-letter queue `auction.events.dlq` with retry (3 attempts, backoff).
- Cross-instance fan-out alternative: Redis pub/sub for STOMP broker relay — pick **RabbitMQ consumer fan-out** and document it in an ADR.

## 12. Security
- BCrypt (strength 12). JWT HS256 with secret from env (min 32 chars), claims: `sub`, `email`, `role`.
- Stateless `SecurityFilterChain`; public: `GET /auctions/**`, `/auth/**`, `/time`, swagger, actuator health.
- Rate limiting on `POST /auctions/{id}/bids` and WS SEND: max 5 bids/second/user (Redis token bucket or Bucket4j). Return `429`.
- CORS configured via env. Input validation with Bean Validation. No stack traces in responses.

## 13. Frontend (React + Vite + TS)
Pages:
1. **Lobby**: grid of live auctions, price/leader/time-left updating live (subscribe `/topic/auctions`), filter by status.
2. **Auction room**: big countdown synced with server (compute `clockOffset` from `/time` and `serverTime` on events), current price with flash animation on new bid, live bid feed, quick-bid buttons (`+min`, `+2×min`, custom), toast for outbid/winning/extended ("⏱ +30s anti-sniping"), connection status indicator, auto-reconnect + state resync via `seq` gap detection.
3. **Login/Register**, **Create auction**, **My bids**.
Design: clean, responsive, dark/light. Use optimistic UI only for the "pending" state, never for price (server is truth).

## 14. Bot simulator (`/bot`)
- Logs in N bot users (`role=BOT`), picks ACTIVE auctions, bids at random intervals (0.5–6s) with randomized increments; more aggressive near the end to trigger anti-sniping.
- Keeps ≥ 3 ACTIVE auctions alive: auto-creates new auctions (ends in 2–5 min) when fewer exist.
- Config via env: `BOT_COUNT`, `BOT_AGGRESSION`, `API_URL`. Runs as docker compose profile `bot`, and in prod so the demo is never empty.
- Bot bids are labeled in UI with a small "bot" tag (be transparent).

## 15. Testing strategy
- **Unit**: bid validation rules, anti-sniping math, JWT service, outbox serialization.
- **Lua script tests**: every return code, boundary on window, max extensions, duplicates.
- **Integration (Testcontainers)**: REST flows with MockMvc/`TestRestTemplate`; Flyway migrations apply cleanly; outbox → RabbitMQ → consumer.
- **WebSocket tests**: `WebSocketStompClient` connects, subscribes, sends bid, asserts broadcast and private ack; unauthorized SEND rejected.
- **Concurrency tests (the showpiece)**:
  1. 100 threads, same auction, random amounts → assert: bids strictly increasing, final `current_price` = max accepted, `bid_count` = number of accepted bids, no duplicate amounts, exactly one leader.
  2. 50 threads sending the **same `bidId`** → exactly 1 persisted bid.
  3. Bids racing the closing job → no bid accepted after `ENDED`; winner = last accepted leader.
  4. Two app instances (two Spring contexts) sharing Postgres/Redis/Rabbit → closing job runs once (ShedLock).
- **Failure test**: force DB failure after Redis accept → Redis state reloaded and consistent.
- Coverage target ≥ 80% on `bid` and `auction` packages (JaCoCo, fail build under threshold).
- **Load test (k6)**: 200 virtual users bidding on 5 auctions for 2 minutes; record p95 latency and error rate in `docs/load-test/RESULTS.md`.

## 16. CI/CD (GitHub Actions)
`ci.yml`: on push/PR → setup Java 21 → `./mvnw verify` (Testcontainers works on `ubuntu-latest`) → upload JaCoCo report → build frontend → build Docker images. On `main`: push images to GHCR and deploy (SSH to VPS running `docker compose -f docker-compose.prod.yml pull && up -d`, or the PaaS deploy hook). Add README badges: build, coverage, license.

## 17. Documentation deliverables
- `README.md` (English): one-line pitch, **live demo link**, GIF of two browser windows bidding, feature list, architecture diagram (mermaid), stack, how to run locally in 3 commands, API/WS reference link, test instructions, load-test numbers, "Design decisions" section (why Lua, why outbox, why RabbitMQ, optimistic vs pessimistic locking, trade-offs), roadmap.
- `docs/architecture.md`: component diagram, bid sequence diagram, state machine diagram.
- `docs/adr/`: 001-redis-lua-for-bid-atomicity, 002-transactional-outbox, 003-rabbitmq-over-kafka, 004-server-authoritative-time.

## 18. Phased plan (one phase at a time)

### Phase 0 — Scaffold (0.5 day)
Repo layout, Maven wrapper, Spring Boot skeleton, Docker Compose (postgres/redis/rabbitmq with healthchecks), `.env.example`, Flyway V1, Actuator, springdoc, GitHub Actions running `verify`, base Testcontainers config.
**DoD**: `docker compose up -d` + `./mvnw verify` green; Swagger opens; CI badge green.

### Phase 1 — Users, auth, auctions CRUD (3 days)
User register/login/refresh/logout, JWT filter, RFC 7807 error handler, auction create/list/detail/cancel, seed data, validation, tests.
**DoD**: full auth flow via Swagger; integration tests for auth and auctions green.

### Phase 2 — Bid engine (4–5 days) ⭐
Redis state loader, `place_bid.lua`, `RedisBidGate`, `BidService` (idempotency, DB tx, outbox write, compensation), REST bid endpoint, anti-sniping, rate limiting, all concurrency tests from §15.
**DoD**: concurrency tests 1–3 and failure test pass reliably (run them 20× in a loop locally); Lua tests cover all codes.

### Phase 3 — Lifecycle + messaging (3 days)
Activation/closing jobs + ShedLock, outbox relay, RabbitMQ topology + DLQ, consumers (fan-out, notifier), `/time` endpoint.
**DoD**: auction auto-starts and auto-ends; two-instance test proves single execution; events flow end to end.

### Phase 4 — WebSocket realtime (3 days)
STOMP config, JWT channel interceptor, bid via WS, private results, public/lobby topics, `seq` numbering, WS tests.
**DoD**: two STOMP clients in a test see each other's bids; unauthorized SEND rejected.

### Phase 5 — Frontend (4–5 days)
Lobby, auction room (countdown with clock offset, price flash, feed, toasts, reconnect + resync), auth pages, create auction, Dockerized with nginx.
**DoD**: open two browser windows → bids appear live in both; killing and restoring the backend reconnects and resyncs.

### Phase 6 — Bot, load test, polish (3 days)
Bot module + compose profile, k6 script and results, metrics dashboard (optional Grafana), ADRs, architecture diagrams.
**DoD**: `docker compose --profile bot up` shows continuous activity; RESULTS.md committed.

### Phase 7 — Deploy and launch (2 days)
Production compose, HTTPS (Caddy/Traefik or PaaS), frontend on Vercel, bot running in prod, README final with GIF + live link, LinkedIn post + "Featured" entry.
**DoD**: public URL works from a phone; README complete; demo never looks empty.

## 19. Prompts to paste into Claude Code (one per phase)
> **Start**: "Read CLAUDE.md and SPEC.md fully. Confirm you understood the rules. Then implement **Phase 0** only. Show your plan first, then execute, then run the build and report results."

> **Next phases**: "Phase N is done and merged. Re-read SPEC.md §<relevant sections> and implement **Phase N+1** only. Plan first, tests alongside code, run `./mvnw verify` at the end, and summarize what changed and any deviations from the spec."

> **Review prompt (after Phase 2)**: "Act as a skeptical senior reviewer. Look for race conditions, double-spend of bids, missed idempotency, transaction-boundary mistakes, and any place Redis and Postgres can diverge. List concrete issues with file/line and propose fixes."

## 20. LinkedIn launch checklist
- Post: the problem (concurrent bids), the solution (Lua + outbox), a GIF, the live link, and the load-test numbers (p95, bids/s).
- Add repo + live demo to "Featured".
- Prepare answers for: Lua vs `synchronized`; optimistic vs pessimistic locking; at-least-once vs exactly-once; what if Redis dies mid-auction; how to scale horizontally; why server-authoritative time.
