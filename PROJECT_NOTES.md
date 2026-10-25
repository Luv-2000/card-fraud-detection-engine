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
| 2 | Data layer | JPA entities, repositories, validation, global error handling, REST endpoints | NEXT | 4-6 h |
| 3 | Kafka | Generator service publishes, fraud-service consumes and stores | Todo | 6-8 h |
| 4 | Fraud rules + Redis | 4 rules, Redis velocity counters, alerts saved | Todo | 8-10 h |
| 5 | JWT security | Login, token validation, protected API, credentials to env vars | Todo | 4-5 h |
| 6 | Tests | JUnit/Mockito for rules, Testcontainers for Postgres/Kafka/Redis | Todo | 6-8 h |
| 7 | CI | GitHub Actions build and test on push | Todo | 2-3 h |
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

### Stage 1: DONE
- Repo root: C:\Users\madhu\Workspace\Projects\card-fraud-detection-engine
- fraud-service: Spring Boot 4.1.2, Maven, package com.luv.fraud_service, main class FraudServiceApplication
- Dependencies so far: spring-web, spring-boot-starter-jdbc, postgresql driver
- docker-compose.yml at repo root: postgres:16, container fraud-postgres, db frauddb, user/pass fraud/fraud, port 5432, volume pgdata
- application.properties: datasource url/username/password for localhost:5432/frauddb
- Endpoints: GET /ping, GET /db-ping (temporary, delete in stage 2)
- Workaround: TimeZone.setDefault("Asia/Kolkata") at the start of main (Asia/Calcutta is rejected by Postgres)
- Run: docker compose up -d, then .\mvnw.cmd spring-boot:run (or IntelliJ run button)

### Concepts covered so far
Spring Boot basics and component scanning, Docker (image, container, volume), Docker Compose, JDBC, driver, JDBC URL, connection pool.

### TODO later
- Move credentials to environment variables before the repo goes public (stage 5).
- Handle the timezone issue in tests (stage 6).
- Delete /db-ping in stage 2.