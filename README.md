# Card Fraud Detection Engine

A real-time, event-driven, rule-based fraud detection pipeline for card transactions. Transactions stream through Kafka, are evaluated against four fraud rules, and are stored in Postgres with an alert for every rule that fires. A JWT-secured REST API exposes the results.

The system flags only. Nothing is blocked or declined. All data is synthetic; no real card data is used.

## Architecture

```
transaction-generator --> Kafka (card-transactions) --> fraud-service --> Postgres (transactions, alerts)
                                                             |
                                                           Redis (velocity counters, last country)
```

- **transaction-generator** publishes synthetic card transactions to Kafka, keyed by card id so each card's transactions stay in order within one partition.
- **fraud-service** consumes each transaction, skips duplicates, saves it, runs the four rules, and saves one alert per rule that fires.
- Rules return an alert or nothing and never touch the database, so each rule is unit tested without any infrastructure.

## Fraud rules

| Rule | Fires when |
|---|---|
| HIGH_AMOUNT | amount is over 50,000 |
| VELOCITY | more than 10 transactions on one card within 60 seconds (Redis counter) |
| COUNTRY_CHANGE | country differs from the card's previous transaction within 30 minutes (Redis) |
| HIGH_RISK_MERCHANT | merchant category is GAMBLING or CRYPTO_EXCHANGE |

Thresholds, windows and the category list are configured in `application.properties`.

## Stack

Java 21, Spring Boot, Apache Kafka, PostgreSQL, Redis, Spring Security (JWT), Docker Compose, JUnit 5, Mockito, Testcontainers, GitHub Actions.

## API

| Endpoint | Auth | Purpose |
|---|---|---|
| `POST /login` | none | Returns a JWT (valid 60 minutes) |
| `GET /ping` | none | Liveness check |
| `GET /alerts`, `GET /alerts/{id}` | JWT | List alerts, or fetch one (404 if missing) |
| `GET /transactions` | JWT | List transactions |
| `GET /cards/{cardId}/alert-summary` | JWT | Alert count, rule names, LLM-written summary |

## Run it

Requires Docker Desktop. No Java or Maven needed.

1. Copy `.env.example` to `.env` and fill in your own values.
   Keep the password hash in single quotes: `APP_PASSWORD_HASH='$2a$10$...'`
2. Start everything:
   ```
   docker compose up -d --build
   ```
3. Log in and call the API (PowerShell):
   ```powershell
   $r = Invoke-RestMethod -Method Post -Uri http://localhost:8080/login -ContentType "application/json" -Body '{"username":"<APP_USERNAME>","password":"<your password>"}'
   Invoke-RestMethod -Uri http://localhost:8080/alerts -Headers @{Authorization="Bearer $($r.token)"}
   ```
4. Stop everything: `docker compose down`

Compose runs Postgres, Redis, Kafka (topic `card-transactions`, 3 partitions, created by `kafka-init`), fraud-service and transaction-generator, with health-checked startup order.

## Testing and CI

- Unit tests (JUnit 5, Mockito) cover all four rules, including boundary cases, and the alert summary service.
- One end-to-end integration test runs against real Postgres, Redis and Kafka containers (Testcontainers). It covers rule firing, skipping of an invalid message, and JWT checks (no token, valid token, tampered token).
- GitHub Actions builds and runs the tests on every push.

## Performance (local load test)

Measured on synthetic transaction data, not real card data.

**Setup:** Windows laptop, Intel Core i5-12450H (8 cores / 12 threads), 16 GB RAM, Docker Desktop (12 CPUs, about 7.6 GB RAM allowed). Single Kafka broker, 3 partitions, one fraud-service instance, everything running in local Docker on the same machine, including the load generator. 2,000 simulated cards.

**Method:** The generator was run with no delay between messages for about 100 seconds, twice. Throughput is transactions saved divided by the time between the first and last transaction timestamp. Alert latency is `alerts.created_at - transactions.timestamp` (generator timestamp to the moment the rule fired, before the alert is saved). The query is in `loadtest/measure.sql`.

| | Run 1 | Run 2 |
|---|---|---|
| Transactions | 15,340 | 14,270 |
| Throughput | 148.7/s | 146.3/s |
| Alert latency p50 | 24.6 ms | 37.8 ms |
| Alert latency p95 | 1,032 ms | 1,602 ms |
| Max alert latency | 2,986 ms | 3,202 ms |
| Consumer lag after the run | 0 | 0 |

**Limits:**
- The generator waits for Kafka's confirmation after every message, so about 146/s is the load it could send, not the maximum fraud-service can handle. The real ceiling was not found.
- Consumer lag moved up and down (up to about 60 per partition) but did not keep growing.
- Latency covers only transactions that produced an alert.
- Per-message console printing was left on in the generator and consumer during the runs.
- Local, single node, plain HTTP. Not a production benchmark.

## Alert summary endpoint (LLM)

`GET /cards/{cardId}/alert-summary` (needs a JWT, like the other endpoints)

Returns the card's alert count, the rule names that fired, and a short summary written by an LLM (Google Gemini API).

Example response shape:

    { "cardId": "CARD-10001", "alertCount": 3, "ruleNames": ["HIGH_AMOUNT", "COUNTRY_CHANGE"], "summary": "..." }

- The rules decide what is an alert. The LLM only reads existing alerts and writes text. It never changes a rule decision.
- If the LLM call fails, times out (10 s), or no key is set, the response still has the real count and rule names, and the summary says "Summary unavailable."
- Only the newest 20 alerts go into the prompt. The count is the full total.
- The prompt (rule names and alert reasons) is sent to Google's API. Only synthetic data is used.
- Needs `LLM_API_KEY` in `.env` (see `.env.example`).
- The summary is generated text and can be wrong.

## Known limitations

- Velocity and country-change windows use processing time, not the transaction timestamp, so replayed old messages would give wrong counts.
- If the transaction is saved but Redis then fails, a retry skips the rules and an alert can be missed. No exactly-once guarantee is claimed.
- Authentication is one configured service account: no roles, no refresh tokens, no login rate limiting, and tokens cannot be revoked before they expire.
- Local, single-node, single-broker, plain HTTP. Not production-ready.