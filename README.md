# Live Auction

[![CI](https://github.com/MatheusHenriquePires/live-auction/actions/workflows/ci.yml/badge.svg)](https://github.com/MatheusHenriquePires/live-auction/actions/workflows/ci.yml)

Real-time auction platform where many users bid at the same time, everyone sees bids instantly,
and the system stays **correct under concurrency**.

Java 21 · Spring Boot 4 · PostgreSQL · Redis · RabbitMQ · WebSocket/STOMP · Testcontainers

> Work in progress. See [SPEC.md](SPEC.md) for the full design and phased plan.

## Run locally

Requirements: Docker, JDK 21.

```bash
cp .env.example .env
docker compose up -d --build        # postgres, redis, rabbitmq, backend
open http://localhost:8080/swagger-ui.html
```

Run the backend from your IDE instead:

```bash
docker compose up -d postgres redis rabbitmq
cd backend && ./mvnw spring-boot:run
```

On first start the backend seeds demo data (`AUCTION_SEED_ENABLED=true`): `admin@example.com`,
`bot1..bot5@example.com` (passwords from `.env`) and 8 sample auctions.

## API at a glance

- `POST /api/v1/auth/register`, `POST /api/v1/auth/login` → access token (JWT, 15 min) + refresh token
  (7 days, rotated on every refresh; reusing an old one revokes all of the user's sessions)
- `GET /api/v1/auctions` and `GET /api/v1/auctions/{id}` are public; creating and cancelling need a token
- Amounts are integer cents, timestamps are UTC
- Errors are RFC 7807 `application/problem+json` with a machine-readable `code` and a `traceId`

## Tests

```bash
cd backend && ./mvnw verify   # Docker must be running (Testcontainers)
```
