# Live Auction

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

## Tests

```bash
cd backend && ./mvnw verify   # Docker must be running (Testcontainers)
```
