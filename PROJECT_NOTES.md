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
Stage 9: LLM alert summary per card (GET /cards/{cardId}/alert-summary). Never changes a rule decision.
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
| 3 | Kafka | Generator service publishes, fraud-service consumes and stores | Done | 6-8 h |
| 4 | Fraud rules + Redis | 4 rules, Redis velocity counters, alerts saved | Done | 8-10 h |
| 5 | JWT security | Login, token validation, protected API, credentials to env vars | Done | 4-5 h |
| 6 | Tests | JUnit/Mockito for rules, Testcontainers for Postgres/Kafka/Redis | Done | 6-8 h |
| 7 | CI | GitHub Actions build and test on push | Done | 2-3 h |
| 7b | Containerize services | Dockerfile per service, compose runs the whole system | Done | 1-2 h |
| 8 | Load test + resume bullets | Measure throughput and latency, bullets from real numbers | Done | 4-5 h |
| 9 | LLM alert summary per card | One read-only endpoint, no async | Done | 4-6 h |

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
(unchanged: Kafka in compose, topic card-transactions with 3 partitions, generator, consumer with existsById duplicate skip, ErrorHandlingDeserializer. Bad messages are logged and skipped.)

### Stage 4: DONE
- Redis (redis:7, container fraud-redis, port 6379, no volume) in docker-compose. Dependency spring-boot-starter-data-redis. StringRedisTemplate used directly.
- Package rules: interface FraudRule { Alert evaluate(Transaction) }, returns Alert or null. Rules never touch Postgres.
- Rules (all @Component, thresholds via @Value constructor params, set in application.properties):
    - HighAmountRule: amount.compareTo(threshold) > 0, threshold 50000 (strictly over)
    - HighRiskMerchantRule: categories GAMBLING,CRYPTO_EXCHANGE, case-sensitive equals
    - VelocityRule: Redis key velocity:<cardId>, INCR, EXPIRE only when count == 1, alert when count > 10, window 60 s
    - CountryChangeRule: Redis key lastcountry:<cardId>, read old value then SET new with 30-minute TTL, alert if previous exists and differs
- TransactionConsumer: existsById skip, save transaction, for-loop over List<FraudRule>, save each non-null Alert.
- Generator: bursts added (about 1 in 300 calls starts 12 consecutive transactions on one card, single sending loop assumed). Other percentages unchanged.
- Verified end to end by hand: all four rule names appear in the alerts table, Redis keys exist with TTLs. Postman tested GET /alerts.

### Stage 4 decisions (with reasons)
- Rules return Alert or null, the consumer saves. Reason: each rule can be unit tested with no Postgres or Kafka. Rejected: rules saving alerts themselves.
- Config in application.properties, values passed through constructors. Reason: thresholds can be changed without editing logic, and tests build rules with plain new. Not "changeable without redeploy": the file is inside the jar.
- Velocity is a fixed window in Redis. Rejected: sliding window (out of scope), Postgres count query per message (database load).
- Country change keeps the previous country in Redis with a TTL. Rejected: querying Postgres for the card's last transaction (extra repository method, query on every message).
- High-amount threshold 50000, because the generator's high amounts start at exactly 1,00,000 and normal ones stop at 5,000.
- Burst generator chosen over lowering velocity N, so the rule is shown on the scenario it exists for. Rejected: N = 5, which fires on ordinary traffic.

### Stage 5: DONE
- Dependencies added: spring-boot-starter-security, spring-boot-starter-oauth2-resource-server (includes Nimbus JWT). No separate JWT library, no custom filter: Spring's built-in bearer token filter reads the token.
- security package: SecurityConfig (CSRF off, stateless, /login and /ping open, everything else needs a valid token, JwtDecoder and JwtEncoder beans using HS256 with the secret), LoginRequest, LoginResponse. controller/AuthController: POST /login.
- Login: one configured user. Username compared with APP_USERNAME, password checked with BCrypt against APP_PASSWORD_HASH. Same 401 message for wrong username or wrong password. Token has sub, iat, exp (60 minutes, app.jwt.expiry-minutes).
- Env vars (in .env, git-ignored; .env.example committed with fake values): POSTGRES_PASSWORD, JWT_SECRET, APP_USERNAME, APP_PASSWORD_HASH. application.properties and docker-compose.yml use placeholders. Postgres volume was recreated with the new password.
- PowerShell: load .env with the loader loop before running from a terminal; IntelliJ needs the variables in the Run Configuration (hash without quotes).
- Tested by hand: login 200, wrong password 401, /alerts with no token 401, with token 200, missing alert 404. Postman collection (local, not in repo) holds login plus alerts and transactions requests, token stored in a collection variable by a post-response script.

### Stage 5 decisions (with reasons)
- One configured user, no users table. Reason: auth is supporting work; stays inside locked scope. Rejected: users table with seeded users (extra entity and repository).
- Spring's built-in resource server instead of a custom JwtAuthFilter. Reason: less code, standard approach, same concepts. Rejected: custom filter with jjwt.
- Swagger UI / springdoc dropped. Reason: user did not want extra code; Postman covers testing. Rejected: springdoc 3.0.x.
- CSRF disabled because the API is stateless and uses bearer tokens, not cookies.

### Resume-defensibility limits found in Stage 5
- Single configured user, no roles, no registration, no refresh tokens. Say "one configured service account".
- No rate limiting on /login. Do not claim brute-force protection.
- A token cannot be revoked before it expires (60 minutes). Changing JWT_SECRET invalidates all tokens.
- Local HTTP only, so the password travels unencrypted. Do not claim transport security.
- Old password fraud/fraud remains in git history (dead, local only).
- Wording to use: "JWT-secured REST API using Spring Security", "passwords stored as BCrypt hashes", "secrets externalized via environment variables". Not "production-grade authentication" or "secure secrets management".

### Resume-defensibility limits found in Stage 4
- Velocity and country-change windows use processing time, not the transaction timestamp. Replayed old messages would give wrong counts.
- INCR and EXPIRE are two calls. A crash between them leaves a counter with no expiry.
- If transaction save succeeds and Redis fails, a retry hits existsById and skips the rules, so an alert can be missed. Do not claim "no missed alerts" or "exactly-once".
- Wording to use: "per-card transaction counter with a time window". Not "sliding window".

### Stage 6: DONE
- Unit tests (JUnit 5 + Mockito) for all 4 rules, 14 tests, in src/test/java/.../rules. Redis rules use a mocked StringRedisTemplate. Boundary cases covered (amount exactly at threshold, count exactly at limit, case-sensitive category).
- One integration test, FraudPipelineIntegrationTest, with Testcontainers (real Postgres 16, Redis 7, Kafka apache/kafka:4.3.1) using @ServiceConnection. Sends JSON to Kafka topic, polls the alerts table with a plain loop (30 x 1 s).
- Also in that test: invalid message (amount -50) is skipped and the next message is still processed; /alerts returns 401 with no token, 200 with a valid token, 401 with a tampered token.
- Test settings in src/test/resources/application-test.properties (fake secrets, profile "test"). Old contextLoads test deleted (covered by the integration test).
- Dependencies added (test scope): spring-boot-testcontainers, testcontainers-junit-jupiter, testcontainers-postgresql, testcontainers-kafka (Testcontainers 2.x artifact names).

### Stage 6 decisions (with reasons)
- Mockito for Redis rules, Testcontainers for one end-to-end test only. Reason: unit tests check rule logic fast; real Redis/Kafka/Postgres behaviour is checked once. Rejected: per-component container tests, controller test suites, coverage targets (repeat the same skill).
- Tests build a token with JwtEncoder instead of calling /login. Reason: avoids needing a real BCrypt hash in test config. Login stays covered by manual Postman tests.
- Separate application-test.properties activated by @ActiveProfiles("test"). Rejected: a test application.properties (would replace the main one).
- Test sets default time zone to UTC in a static block (main() is skipped in tests).

### Resume-defensibility limits found in Stage 6
- Say "unit and integration tests (JUnit, Mockito, Testcontainers)". Do not quote coverage percentages: none measured.
- Mocked Redis proves rule logic only. Expiry and counting are exercised by the integration test for the high-amount path only, not for velocity or country change windows.
- Login success path has no automated test.

### Stage 7: DONE
- .github/workflows/ci.yml at repo root. Trigger: push (all branches). One job "build" on ubuntu-latest, working-directory fraud-service. Steps: actions/checkout@v4, actions/setup-java@v4 (temurin 21, cache: maven), ./mvnw verify. First run green.
- No secrets in CI: tests use fake values in application-test.properties. Docker is preinstalled on the runner, so Testcontainers needs no setup.
- Fixed for Linux: mvnw was stored in git as 100644, changed to 100755 with git update-index --chmod=+x. .gitattributes already forces LF for mvnw.
- Found and committed Testcontainers test dependencies in pom.xml that were left uncommitted from Stage 6 (CI would have failed without them).

### Stage 7 decisions (with reasons)
- One job, fraud-service only. Reason: it is the only module with tests; transaction-generator has none. Rejected: second job or matrix for the generator (nothing to test).
- Trigger on push only. Reason: solo repo. Rejected: pull_request trigger.
- cache: maven. Reason: faster later runs.

### Resume-defensibility limits found in Stage 7
- CI builds and tests only. No deployment, so do not say "CI/CD".
- transaction-generator is not built or tested in CI.
- A green build is only as strong as the tests: no coverage measured.
- Wording to use: "GitHub Actions CI builds and runs unit and integration tests on every push". Do not add a build badge until it is shown green in the README.

### Stage 7b: DONE
- Dockerfile in fraud-service and transaction-generator: multi-stage (eclipse-temurin:21-jdk builds the jar with ./mvnw package -DskipTests, 21-jre runs it). .dockerignore in both (target, .git, .env, .idea, *.iml).
- docker-compose.yml now runs 6 containers: postgres, kafka, kafka-init (creates topic card-transactions, 3 partitions, then exits), redis, fraud-service, transaction-generator.
- Kafka has two listeners: HOST (localhost:9092, for IntelliJ and tests) and DOCKER (kafka:29092, for containers). Containers get SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092.
- Containers override localhost through env vars: SPRING_DATASOURCE_URL, SPRING_DATA_REDIS_HOST, SPRING_KAFKA_BOOTSTRAP_SERVERS. application.properties unchanged, so IntelliJ still works.
- Healthchecks: pg_isready, redis-cli ping, kafka-topics.sh --list. fraud-service waits for all three plus kafka-init (service_completed_successfully). Generator waits for kafka and kafka-init.
- Secrets come from .env through compose. APP_PASSWORD_HASH must be in single quotes in .env.
- Tested by hand: login 200, alerts, transactions, 401 without token.

### Stage 7b decisions (with reasons)
- Build the jar inside Docker (multi-stage). Reason: a fresh clone has no target folder, so only Docker is needed to run it. Rejected: COPY of a locally built jar.
- Topic created by a one-shot kafka-init container. Reason: Kafka has no volume, so the topic vanished on down, and auto-creation would give 1 partition. Rejected: creating the topic in Java code (NewTopic bean), a Kafka volume.
- Two Kafka listeners instead of one. Reason: Kafka replies with an advertised address, which must work from where the client runs.
- Docker build skips tests (-DskipTests). Reason: tests run in GitHub Actions CI.

### Resume-defensibility limits found in Stage 7b
- Healthchecks control startup order only. Nothing restarts a failed container, so do not claim "self-healing" or "fault tolerant".
- Still local, single node, single broker, plain HTTP. Do not claim "production-ready".
- Kafka has no volume: messages are lost on docker compose down.
- Image sizes not measured, so do not quote them.
- Wording to use: "containerized both services with multi-stage Docker builds; Docker Compose runs the full system (Kafka, Postgres, Redis, two services) with health-checked startup order".

### Stage 8: DONE
- Edits: spring.jpa.show-sql=false in fraud-service. Generator reads generator.delay-ms (default 500) and generator.card-count (default 20) from properties; compose passes GENERATOR_DELAY_MS and GENERATOR_CARD_COUNT from the shell (defaults 500 and 20).
- loadtest/measure.sql prints: transaction count, span in seconds, tx/s, alert latency p50/p95/max, alerts per rule. Run with: Get-Content loadtest\measure.sql | docker exec -i fraud-postgres psql -U fraud -d frauddb
- Procedure per run: docker compose down, up everything except the generator, wait 60 s, TRUNCATE alerts, transactions, run the generator with delay 0 and 2000 cards for about 90 s, stop it, check consumer lag is 0, run measure.sql.
- Results (two runs, delay 0, 2000 cards): 148.7/s and 146.3/s; alert latency p50 24.6 and 37.8 ms, p95 1,032 and 1,602 ms, max 2,986 and 3,202 ms; lag 0 after each run; 15,340 and 14,270 transactions.
- Setup: i5-12450H (8 cores/12 threads), 16 GB RAM, Docker 12 CPUs / about 7.6 GB, single Kafka broker, 3 partitions, one fraud-service instance, generator on the same machine.
- Performance section added to README.

### Stage 8 decisions (with reasons)
- Measure with the existing timestamps and SQL, no new tool. Reason: transaction.timestamp and alert.createdAt already hold what latency needs. Rejected: JMeter, Gatling, k6, Prometheus/Grafana (extra learning, no extra resume value, out of scope).
- Card count made configurable (2,000 for the test). Reason: with 20 cards at 10 tx/s the velocity rule fired on about two thirds of transactions, which is not realistic. Rejected: keeping 20 cards.
- Two runs, lower figures reported. Reason: one run can be a fluke (warm-up outliers seen). Rejected: a single run, tuning the consumer to look better.
- A 10 tx/s run was dropped from the results. Its rate (4.7/s) did not match its own offsets and was not explained.

### Resume-defensibility limits found in Stage 8
- About 146/s is the generator's send rate, not fraud-service's ceiling (the generator waits for Kafka's confirmation per message). Say "sustained about 146 transactions/s with no growing backlog", not "can handle" or "maximum".
- Lag rose and fell (up to about 60 per partition) but ended at 0. The p95 of 1.0 to 1.6 s is probably messages waiting in Kafka: an inference, not measured.
- Latency covers only transactions that raised an alert, from the generator timestamp to the moment the rule fired (before the alert is saved).
- Generator and system share one machine. Local Docker, single broker, single instance. Not a production benchmark.
- Per-message System.out.println in the consumer and producer was left in place during the runs.
- Do not claim "real-time" without the numbers and setup next to it.
- Wording to use: "Load-tested on synthetic transaction data (single machine, local Docker, single Kafka broker): sustained about 146 transactions/s over roughly 100 s runs with no growing consumer backlog; median alert latency 25 to 38 ms, p95 1.0 to 1.6 s, measured from transaction creation to rule evaluation."

### Stage 9: DONE
- New endpoint GET /cards/{cardId}/alert-summary (JWT protected). Returns cardId, alertCount, ruleNames, summary.
- AlertRepository.findAlertsByCardId: JPQL joining Alert to Transaction on transactionId, filtered by cardId, newest first. Named parameter. No schema change.
- Package llm: LlmClient (Spring RestClient, Gemini generateContent, 10 s connect and read timeout, returns null on any failure or empty key, logs only the exception class), AlertSummaryService (builds prompt from the newest 20 alerts, dedupes rule names, fallback "Summary unavailable."), AlertSummaryResponse (record). controller/CardController.
- Config: llm.api-key (from env LLM_API_KEY), llm.model, llm.timeout-seconds in application.properties. LLM_API_KEY in .env, .env.example (fake), docker-compose.yml (default empty).
- Tests: AlertSummaryServiceTest, 4 unit tests with mocked AlertRepository and LlmClient (summary and dedupe, LLM failure fallback, no alerts skips LLM, 20-alert cap with full count). mvnw verify passes.
- Tested by hand in Postman: success case. The wrong-key failure case was not run by hand.

### Stage 9 decisions (with reasons)
- One read-only summary endpoint instead of an async explainer. Reason: simpler, no background threads, and rule decisions cannot be affected. Rejected: async explanation saved on each alert.
- No cardId column on alerts; join through transactions. Reason: keeps the card stored once, no change to Alert or the rules. Rejected: copying cardId into alerts.
- LLM failure returns 200 with real count and rules, summary "Summary unavailable.". Reason: the data from our own database is still correct. Rejected: returning 500.
- Only the newest 20 alerts go into the prompt. Reason: bounds request size and cost. Count still shows the full total.
- Gemini free tier via Google AI Studio. Reason: no card needed. Rejected: Claude API (no free tier).
- Swagger/springdoc stayed dropped; Postman only.

### Resume-defensibility limits found in Stage 9
- Summary is generated text and is not checked. Do not say "accurate" or that the AI detects fraud.
- No caching and no rate limit on the endpoint: every call is one Gemini request.
- Unit tests mock the LLM client. A real timeout or Google error path was not tested by hand.
- Free-tier limits change; quote none.
- Data is synthetic, and it is sent to an external API.
- Wording to use: "Added a JWT-secured endpoint that returns an LLM-generated summary of a card's alerts (Gemini API, synthetic data); rule decisions are deterministic and unaffected; falls back to counts and rule names if the LLM is unavailable."

### Next step
All stages done. Remaining: final README pass and resume bullets from the Stage 8 and Stage 9 wording.

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