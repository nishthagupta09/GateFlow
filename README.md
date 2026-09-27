# GateFlow

### Custom API Gateway & Distributed Traffic Management System

GateFlow is a custom-built API gateway implemented from scratch using Java and Spring Boot.

It acts as a reverse proxy between clients and backend services while providing traffic management, resilience, observability, and distributed-systems features.

---

## Overview

GateFlow sits between clients and backend services and is responsible for:

- Routing incoming requests to backend services
- Distributing traffic across multiple service instances
- Detecting unhealthy service instances
- Preventing repeated requests to failing services
- Retrying transient downstream failures
- Limiting excessive client traffic
- Maintaining request traceability
- Preventing duplicate payment operations
- Mirroring selected traffic to a shadow instance
- Exposing metrics for monitoring and analysis

The project is designed to demonstrate how production-style API gateway and distributed-systems concepts can be implemented without using a pre-built gateway framework.

---

## Architecture

```text
                         ┌──────────────────┐
                         │      Client      │
                         └────────┬─────────┘
                                  │
                                  ▼
                    ┌─────────────────────────┐
                    │        GateFlow         │
                    │      API Gateway        │
                    │         :8080           │
                    └────────────┬────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              │                  │                  │
              ▼                  ▼                  ▼
       ┌────────────┐     ┌────────────┐     ┌────────────┐
       │   User     │     │  Payment   │     │  Payment   │
       │  Service   │     │  Service   │     │  Service   │
       │   :8081    │     │   :8082    │     │   :8083    │
       └────────────┘     └────────────┘     └────────────┘
                               │
                               ▼
                         ┌────────────┐
                         │  Payment   │
                         │  Service   │
                         │   :8084    │
                         └────────────┘

                         Shadow Traffic
                               │
                               ▼
                         ┌────────────┐
                         │  Payment   │
                         │  Service   │
                         │   :8085    │
                         └────────────┘

              ┌─────────────────────────────────────┐
              │              Redis                  │
              │ Rate Limiting / Idempotency         │
              └─────────────────────────────────────┘

              ┌─────────────────────────────────────┐
              │ Prometheus        Grafana           │
              │ Metrics            Dashboards       │
              └─────────────────────────────────────┘
```

---

## Services

| Service | Port | Purpose |
|---|---:|---|
| GateFlow Gateway | `8080` | Request routing and traffic management |
| User Service | `8081` | User-related API |
| Payment Service | `8082` | Primary payment instance |
| Payment Service | `8083` | Payment instance |
| Payment Service | `8084` | Payment instance |
| Payment Service | `8085` | Shadow traffic instance |
| Redis | `6379` | Rate limiting and idempotency |
| Prometheus | `9090` | Metrics collection |
| Grafana | `3000` | Metrics visualization |

---

# Key Features

## 1. Reverse Proxy

GateFlow forwards client requests to the appropriate downstream service.

Example:

```text
GET /api/users/1
        │
        ▼
GateFlow :8080
        │
        ▼
User Service :8081
        │
        ▼
GET /users/1
```

The gateway constructs the downstream URL while preserving the request method, query parameters, body, and relevant headers.

---

## 2. Configuration-Based Routing

Routes are defined using `application.yml`.

Example:

```yaml
gateway:
  routes:
    - path: /api/users/**
      service: user-service
      targets:
        - url: http://localhost:8081
          weight: 100

    - path: /api/payments/**
      service: payment-service
      targets:
        - url: http://localhost:8082
          weight: 80
        - url: http://localhost:8083
          weight: 10
        - url: http://localhost:8084
          weight: 10
```

This keeps routing configuration separate from the gateway's request-processing logic.

---

## 3. Health-Aware Routing

GateFlow periodically checks the health of downstream service instances.

Each service exposes:

```text
GET /health
```

The gateway periodically checks these endpoints and maintains the health status of each target.Unhealthy targets are excluded from request routing.

Health checks run every 5 seconds.

---

## 4. Weighted Canary Routing

GateFlow supports weighted traffic distribution between multiple instances.

The current payment-service configuration is:

```text
8082 → 80%
8083 → 10%
8084 → 10%
```

A test using **500 requests** produced:

| Instance | Requests | Observed Traffic |
|---|---:|---:|
| `8082` | 390 | 78.0% |
| `8083` | 56 | 11.2% |
| `8084` | 54 | 10.8% |
| **Total** | **500** | **100%** |

The observed distribution is close to the configured 80/10/10 weighting.

---

## 5. Circuit Breaker

GateFlow implements an in-memory circuit breaker for each downstream target.

The circuit has three states:

```text
CLOSED
   │
   │ repeated failures
   ▼
 OPEN
   │
   │ recovery timeout
   ▼
HALF_OPEN
   │
   ├── success ──► CLOSED
   │
   └── failure ──► OPEN
```

Current configuration:

```text
Failure threshold: 3 failures
Recovery timeout: 10 seconds
```

When a target reaches the failure threshold, GateFlow stops sending normal requests to that target.

After the recovery period, one test request is allowed through.A successful test closes the circuit again.

---

## 6. Retry with Exponential Backoff

GateFlow retries transient downstream failures.

The gateway:

- Retries downstream `5xx` responses
- Retries connection/transport failures
- Uses a maximum of 3 attempts
- Uses exponential backoff with jitter
- Retries `GET`, `HEAD`, and `OPTIONS`
- Retries `POST` only when an `Idempotency-Key` is provided. This avoids blindly retrying non-idempotent operations such as payments.

---

## 7. Redis-Based Rate Limiting

GateFlow implements fixed-window rate limiting using Redis.

Current configuration:

```yaml
rate-limit:
  capacity: 100
  window-seconds: 60
```

This means a client can make up to:

```text
100 requests / 60 seconds
```

Requests exceeding the limit receive:

```text
HTTP 429 Too Many Requests
```

The rate-limit counter uses a Redis Lua script so the increment and expiry logic are executed atomically.

---

## 8. Request ID Tracing

Every request receives an `X-Request-ID`.

If a client already provides one, GateFlow preserves it.

Otherwise, GateFlow generates a new request ID.

Example:

```text
Client
  │
  │ X-Request-ID: abc123
  ▼
GateFlow
  │
  │ X-Request-ID: abc123
  ▼
Downstream Service
```

The request ID is also included in gateway logs through MDC logging.This makes it easier to trace a request across the gateway and downstream services.

---

## 9. Payment Idempotency

Payment requests support idempotency using:

```text
Idempotency-Key
```

The payment service stores idempotency state in Redis.

Example:

```text
First request
Idempotency-Key: payment-123
        │
        ▼
   PROCESSING
        │
        ▼
   COMPLETED
        │
        ▼
Repeated request
        │
        ▼
Return previous result
```

This allows repeated requests with the same idempotency key to reuse the original result instead of creating a new payment.

---

## 10. Request Fingerprinting

The payment service generates a SHA-256 fingerprint of the payment request body.This prevents an existing idempotency key from being reused with a different request.

For example:

```text
Idempotency-Key: payment-123

Request A:
{
  "userId": 1,
  "amount": 100
}

Request B:
{
  "userId": 1,
  "amount": 999
}
```

The second request is rejected because the request fingerprint does not match the original request.

---

## 11. Shadow Traffic

GateFlow supports shadow traffic for payment requests.

A request can be processed normally by the primary payment service while simultaneously being mirrored to a separate shadow instance.

```text
                 ┌──► Payment Service :8082
                 │
Client ──► GateFlow
                 │
                 └──► Shadow Payment Service :8085
```

The shadow instance receives:

```text
X-Shadow-Request: true
```

and simulates the payment operation without performing the normal payment side effects.

This allows new service versions or experimental implementations to receive realistic traffic without affecting the primary system.

---

# Observability

GateFlow exposes metrics through:

```text
/actuator/prometheus
```

Prometheus scrapes the gateway every 5 seconds.Grafana is used to visualize the metrics.

## Current Metrics

### Request Metrics

```text
gateflow_requests_total
gateflow_request_latency
```

### Retry Metrics

```text
gateflow_retries_total
```

### Rate Limiting

```text
gateflow_rate_limit_rejections_total
```

### Downstream Traffic

```text
gateflow_downstream_requests_total
```

### Shadow Traffic

```text
gateflow_shadow_requests_total
gateflow_shadow_failures_total
```

---

# Grafana Dashboard

The current dashboard tracks:

- Total GateFlow requests
- Request rate
- Average request latency
- Retry attempts
- Rate-limit rejections
- Canary traffic distribution
- Shadow requests
- Shadow failures
- Shadow success rate

<img width="1901" height="785" alt="image" src="https://github.com/user-attachments/assets/6b3c7a9f-4c35-4243-8c22-4dba7eb88880" />
<img width="1907" height="397" alt="image" src="https://github.com/user-attachments/assets/10c30639-0e18-40da-a984-fc9c7ec1ccd5" />
<img width="1902" height="772" alt="image" src="https://github.com/user-attachments/assets/22a91657-4a94-4cd3-a43d-b3f1999a5c45" />



---

# Testing

GateFlow includes automated tests for important gateway behaviour.

Current tests include:

### Gateway Integration Tests

- Successful user request proxying
- User-not-found response propagation

### Circuit Breaker Test

- Circuit opens after three consecutive failures

### Payment Idempotency Test

- Idempotency result storage
- Result reuse
- Request fingerprint matching
- Rejection of mismatched requests

Run gateway tests:

```bash
cd gateway
mvn test
```

Run payment-service tests:

```bash
cd payment-service
mvn test
```

---

# CI/CD

GateFlow uses GitHub Actions for automated builds and tests.

The CI pipeline:

1. Checks out the repository
2. Sets up Java 21
3. Starts Redis
4. Builds the User Service
5. Starts the User Service
6. Waits for the service health endpoint
7. Runs Gateway tests
8. Runs Payment Service tests

The pipeline runs on:

```text
push → main
pull request → main
```

---

# Tech Stack

### Backend

- Java 21
- Spring Boot
- Spring Web
- Spring Data Redis
- Maven

### Infrastructure

- Redis
- Docker
- Docker Compose

### Observability

- Micrometer
- Prometheus
- Grafana
- Spring Boot Actuator

### Testing & CI

- JUnit 5
- GitHub Actions

### API Development

- Postman
- Swagger/OpenAPI

---

# Project Structure

```text
GateFlow/
│
├── gateway/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/gateflow/gateway/
│   │   │   │       ├── config/
│   │   │   │       ├── controller/
│   │   │   │       ├── health/
│   │   │   │       ├── loadbalancing/
│   │   │   │       ├── metrics/
│   │   │   │       ├── ratelimit/
│   │   │   │       ├── routing/
│   │   │   │       ├── shadow/
│   │   │   │       └── ...
│   │   │   └── resources/
│   │   │       └── application.yml
│   │   │
│   │   └── test/
│   │       └── java/
│   │
│   └── pom.xml
│
├── user-service/
│   ├── src/
│   └── pom.xml
│
├── payment-service/
│   ├── src/
│   └── pom.xml
│
├── monitoring/
│   └── prometheus.yml
│
├── docker-compose.yml
├── .github/
│   └── workflows/
│       └── ci.yml
│
└── README.md
```

---

# Getting Started

## Prerequisites

Make sure the following are installed:

- Java 21
- Maven
- Redis
- Docker Desktop
- Git

---

## 1. Clone the Repository

```bash
git clone https://github.com/nishthagupta09/GateFlow.git
cd GateFlow
```

---

## 2. Start Redis

Make sure Redis is running on:

```text
localhost:6379
```

If using Docker:

```bash
docker run -d \
  --name gateflow-redis \
  -p 6379:6379 \
  redis:7
```

---

## 3. Start User Service

```bash
cd user-service
mvn spring-boot:run
```

The service runs on:

```text
http://localhost:8081
```

---

## 4. Start Payment Service Instances

Start the first instance:

```bash
cd payment-service
mvn spring-boot:run
```

Start additional instances using different ports:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8083"
```

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8084"
```

Start the shadow instance:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8085"
```

---

## 5. Start GateFlow

```bash
cd gateway
mvn spring-boot:run
```

GateFlow runs on:

```text
http://localhost:8080
```

---

# Example Requests

## User Request

```http
GET http://localhost:8080/api/users/1
```

GateFlow forwards the request to:

```text
http://localhost:8081/users/1
```

---

## Payment Request

```http
POST http://localhost:8080/api/payments
Idempotency-Key: payment-001
Content-Type: application/json
```

Request body:

```json
{
  "userId": 1,
  "amount": 100.00,
  "currency": "INR"
}
```

GateFlow routes the request to one of the configured payment-service instances.

---

# Monitoring

Start Prometheus and Grafana:

```bash
docker compose up -d
```

Prometheus:

```text
http://localhost:9090
```

Grafana:

```text
http://localhost:3000
```

Gateway metrics:

```text
http://localhost:8080/actuator/prometheus
```

---

# Future Improvements

Planned improvements include:

- Adaptive load balancing based on downstream latency and failures
- More realistic User and Payment service implementations
- Persistent payment records and transaction states
- Docker Compose setup for the complete system
- Expanded integration testing
- Improved circuit-breaker metrics
- Configuration-driven shadow targets
- Performance benchmarking
- Architecture and sequence diagrams

---

# License

This project is intended for educational and portfolio purposes.
