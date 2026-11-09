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

### Stage 3: IN PROGRESS (concepts 1-7 done, no Java code yet)
- Kafka runs in docker-compose.yml next to Postgres: image apache/kafka:4.3.1, container kafka, port 9092 (localhost:9092 from the host). No volume: `docker compose down` deletes topics; stop/start keeps them.
- Topic card-transactions created by hand: 3 partitions, replication factor 1. A scratch topic was used to test keys and then deleted.
- Verified in the console: same key always lands in the same partition (CARD-10482 both in partition 0).
- Run: docker compose up -d (postgres + kafka). If Kafka was removed, recreate the topic with kafka-topics.sh --create --topic card-transactions --partitions 3 --replication-factor 1 --bootstrap-server localhost:9092 (via docker exec kafka /opt/kafka/bin/...).

### Stage 3 decisions (with reasons)
- Message key = cardId. Same card goes to the same partition, so the velocity and country-change rules see one card's transactions in order. Ordering is per card only, not global. Rejected: no key (order across a card's transactions lost).
- 3 partitions, fixed. Allows up to 3 fraud-service instances in the group. Changing the count later changes which partition a key maps to. Rejected: letting Kafka auto-create the topic (defaults to 1 partition).
- Consumer group id: fraud-service. Committed offsets are stored by Kafka per group per partition.
- Delivery is at-least-once (retries and crashes before commit can cause duplicates). Duplicates are handled using Transaction.id (the generator's UUID). Not claiming exactly-once.
- Message value = JSON. Spring Boot 4 uses Jackson 3 (dates as ISO-8601 strings). Use Spring Kafka 4.x JacksonJsonSerializer / JacksonJsonDeserializer (target type Transaction), not the older JsonSerializer / JsonDeserializer. Key = string serializer. Rejected: Avro / schema registry (out of scope).
- Single local broker, replication factor 1: no fault tolerance. Resume wording: "Kafka broker in Docker Compose (single node)", never "cluster", "fault-tolerant" or "guaranteed delivery".

### Concepts covered (Stage 3)
Message broker, producer, consumer, topic, log, offset, committed offset, retention, partitions, keys, consumer groups, rebalance, acknowledgements and retries, serialization and deserialization, JSON.

### Next steps
1. Concept 8: create the transaction-generator Spring Boot project (Spring Initializr, Spring for Apache Kafka), producer with KafkaTemplate, key = cardId, wait for ack.
2. Concept 9: fraud-service consumer (@KafkaListener, group id fraud-service) saving to Postgres.
3. Concept 10: duplicates (same Transaction.id) and bad messages (the validation TODO) in the consumer.

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