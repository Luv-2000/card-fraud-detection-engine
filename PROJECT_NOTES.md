# Card Fraud Detection Engine: Project Notes

## Purpose
Resume project, built from scratch to learn each technology while building it. Real-time, event-driven, rule-based card transaction fraud detection on synthetic data.

## Scope (locked)

### Services
- transaction-generator: publishes synthetic card transactions to a Kafka topic. No database.
- fraud-service: consumes transactions, evaluates rules, stores transactions and alerts in Postgres, exposes a JWT-secured REST API.

### Data
- Transaction: id, cardId, amount, currency, merchantCategory, country, timestamp
- Alert: id, transactionId, ruleName, reason, createdAt

### Rules (exactly 4)
1. High amount: over a fixed threshold
2. Velocity: more than N transactions per card in a time window (Redis counter)
3. Country change: different country from the card's previous transaction within X minutes
4. High-risk merchant category: category on a configured list

Behavior: flag only. A triggered rule creates an Alert row. Nothing is blocked or declined.

### API
Login (returns JWT), list alerts, get one alert, list transactions.

### Out of scope
Batch processing, ML models, frontend, API gateway, service discovery, Kubernetes, multi-broker Kafka, cloud deployment.

### Optional
Stage 9: async AI alert explainer. Never changes a rule decision.

## Resume-defensibility rules
- Say "synthetic transaction data". Never imply real card data.
- Every resume number comes from my own stage 8 load test, with the setup noted (machine, single broker, local Docker).
- Claim "real-time" only with measured end-to-end latency.
- Do not claim "production-ready". It is local and single-node.

## Stages
| # | Stage | Scope | Status | Est. |
|---|---|---|---|---|
| 1 | Setup | Spring Boot, Postgres in Docker, first endpoint, DB connection | DONE | - |
| 2 | Data layer | JPA entities, repositories, validation, global error handling, REST endpoints | DONE | 4-6 h |
| 3 | Kafka | Generator service publishes, fraud-service consumes and stores | Todo | 6-8 h |
| 4 | Fraud rules + Redis | 4 rules, Redis velocity counters, alerts saved | Todo | 8-10 h |
| 5 | JWT security | Login, token validation, protected API, credentials to env vars | Todo | 4-5 h |
| 6 | Tests | JUnit/Mockito for rules, Testcontainers for Postgres/Kafka/Redis | Todo | 6-8 h |
| 7 | CI | GitHub Actions build and test on push | Todo | 2-3 h |
| 7b | Containerize services | Dockerfile per service, compose runs the whole system | Todo | 1-2 h |
| 8 | Load test + resume bullets | Measure throughput and latency, bullets from real numbers | Todo | 4-5 h |
| 9 | AI alert explainer | Async, optional | Optional | 4-6 h |

## Teaching instructions (for Claude)
- Guide step by step: exactly what to do next, which files to create, which commands to run (Windows PowerShell compatible).
- Teach what each tool or concept is and why we use it BEFORE giving commands.
- One concept at a time, explained directly (no analogies first). Beginner-friendly pace.
- After each concept, a short comprehension check before moving on. Grade my answers honestly.
- Keep code simple: no unnecessary abstraction, plain for-loops over streams.
- Be crisp and honest. Flag anything that would not be defensible on a resume.
- Check version-specific details (Spring Boot 4.x) before giving code. Do not rely on older 3.x examples.
- At the end of each stage, give me an updated state block for this file.
- Keep steps clean and in order. Do not re-list or restate earlier steps.

## Current state

### Environment
Windows, PowerShell, IntelliJ IDEA, Java 21 (Temurin), Docker Desktop. Maven via wrapper (mvnw.cmd), no global Maven.

### Stages 1-2: DONE
- Repo root: C:\Users\madhu\Workspace\Projects\card-fraud-detection-engine
- fraud-service: Spring Boot 4.1.2, Maven, package com.luv.fraud_service
- Dependencies: spring-web, spring-boot-starter-data-jpa (replaced starter-jdbc, which it includes), spring-boot-starter-validation, postgresql driver
- docker-compose.yml at repo root: postgres:16, container fraud-postgres, db frauddb, user/pass fraud/fraud, port 5432, volume pgdata
- application.properties: datasource settings, spring.jpa.hibernate.ddl-auto=update, spring.jpa.show-sql=true, spring.jpa.properties.hibernate.format_sql=true
- Packages: entity (Transaction, Alert), repository (TransactionRepository, AlertRepository), controller (HealthController, TransactionController, AlertController), exception (ResourceNotFoundException, ErrorResponse, GlobalExceptionHandler)
- Endpoints: GET /ping, GET /transactions, GET /alerts, GET /alerts/{id} (404 with ErrorResponse JSON if missing). /db-ping deleted.
- Workaround: TimeZone.setDefault("Asia/Kolkata") at the start of main (Asia/Calcutta is rejected by Postgres)
- Run: docker compose up -d, then .\mvnw.cmd spring-boot:run (or IntelliJ run button)

### Stage 3: DONE
- Kafka (apache/kafka:4.3.1, localhost:9092) in docker-compose. Topic card-transactions: 3 partitions, replication factor 1.
- transaction-generator: continuous. TransactionFactory builds random transactions (20 cards CARD-10001..10020, amounts Rs 100-5,000 with ~1 in 50 at Rs 1,00,000-5,00,000, home country IN with ~1 in 20 foreign, ~1 in 25 risky category). Sends every 500 ms, key = cardId, still waits with .get(). Percentages are placeholders, to tune in Stage 4.
- fraud-service consumer: skips duplicates with existsById before save. Value deserializer is ErrorHandlingDeserializer wrapping JacksonJsonDeserializer. A bad message (tested with "not-json") no longer blocks the consumer; it is logged and skipped.

### Stage 3 decisions added (with reasons)
- Duplicate skip via existsById. Rejected: relying on save() merge (silently updates; in Stage 4 a redelivered message would create duplicate alerts and double-count the Redis velocity counter). Safe because one cardId always goes to one partition and there is one consumer. Not "exactly-once".
- ErrorHandlingDeserializer added after a bad message made the consumer loop forever (observed). Skipped messages are only logged. Rejected: dead-letter topic (out of scope). Resume wording: "logs and skips undeserializable messages".
- Generator amounts built in paise as long, then BigDecimal.valueOf(paise, 2). Currency is always INR, even for foreign countries (no rule uses currency).

### Unverified / TODO
- Behavior for a readable message that fails validation (e.g. amount -50) was NOT tested. Test in Stage 6 and handle then.
- Still open: keep or turn off show-sql (Stage 8); credentials to env vars before repo goes public (Stage 5); timezone issue in tests (Stage 6).
- Git: Stage 3 changes committed, not pushed.

### Next step
Stage 4, Concept 1: Redis (what it is, why velocity counting needs it).

### Decisions (with reasons)
- Transaction.id is a UUID made by the generator, not the database. The transaction exists before it reaches Postgres, and Kafka can deliver a message twice; a DB-made ID would turn one transaction into two rows. Rejected: DB-generated ID.
- Alert.id is a Long generated by Postgres (IDENTITY). Alerts are created inside fraud-service at save time, so nothing needs the ID earlier.
- One alert = one transaction + one rule. A transaction can have 0 to 4 alerts. Velocity alert attaches to the transaction that pushed the count over N, with the window in the reason text.
- Alert.transactionId is a plain UUID column, not @ManyToOne. Keeps things simple; Postgres does not enforce that the transaction exists. Rejected: JPA relationship (lazy loading and joins not needed).
- amount is BigDecimal(12,2), not double (rounding errors). timestamp and createdAt are Instant (UTC, avoids timezone issues).
- ddl-auto=update for local development only. It only adds, never drops or renames, and keeps no history. Not production practice; do not claim it on the resume. Rejected: Flyway/Liquibase (out of scope).
- Controllers call repositories directly, no service layer. Entities are returned as JSON directly (acceptable at this size).
- Global error handling covers only ResourceNotFoundException (404). Rejected: catch-all Exception handler, because it turned unknown URLs into 500s; Spring's defaults already hide internals and log errors.
- Validation annotations (@NotNull, @NotBlank, @Positive, @Size) are on Transaction. Hibernate checks them on save through the app only; direct inserts in psql bypass them. Not yet exercised.

### Concepts covered so far
Spring Boot basics and component scanning, Docker, Docker Compose, JDBC, connection pool, JPA vs Hibernate vs Spring Data JPA, entities, repositories, ID generation, REST controllers, path variables, Optional, dependency injection via constructor, Jackson, validation annotations, exceptions and @RestControllerAdvice.

### TODO later
- Move credentials to environment variables before the repo goes public (stage 5).
- Handle the timezone issue in tests (stage 6).
- Handle validation failures in the Kafka consumer, not the REST handler (stage 3).
- Test that validation rejects bad data (stage 6).
- Decide whether to keep show-sql on; it adds noise during the load test (stage 8).