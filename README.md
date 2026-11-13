# BookMyTickets - Ticket Booking Platform (Java microservices on AWS EKS)

Production-grade, event-driven ticket booking backend (movies, concerts, sports) designed for millions of users:
**no double-booked seats, no double charges, idempotent end to end, rate-limited and throttled at every layer.**

* Java 21 (virtual threads) · Spring Boot 3.5 · Spring Cloud 2025.0 (Gateway, OpenFeign, LoadBalancer, Kubernetes) · gRPC · Kafka · PostgreSQL · Redis · Resilience4j · Stripe · Amazon SES
* Docker images -> Amazon ECR · Kubernetes manifests for Amazon EKS (`k8s/`)
* Docs: **[Architecture & design decisions](docs/ARCHITECTURE.md)** (incl. §9 Rate limiting & throttling) · **[Microservice patterns catalogue](docs/MICROSERVICE-PATTERNS.md)**

## Repository layout

```
ticket-booking-platform/
├── pom.xml                      # parent (BOMs, versions)
├── common-lib/                  # JWT security, RFC7807 errors, outbox/inbox, distributed token bucket, event contracts
├── grpc-api/                    # protobuf contracts + gRPC JWT interceptor / call credentials
├── discovery-server/            # Eureka (local only; EKS uses the Kubernetes API)
├── api-gateway/                 # Spring Cloud Gateway: routing, JWT, Redis rate limiting, load shedding, circuit breakers
├── catalog-service/             # events/venues/shows, Redis cache-aside, REST + gRPC
├── seat-inventory-service/      # seat map + atomic seat holds (Redis Lua + DB locks), gRPC
├── booking-service/             # saga orchestrator, Idempotency-Key, Feign + gRPC clients, ticket codes
├── payment-service/             # Stripe behind an anti-corruption layer, webhooks, refunds, outbound throttling
├── notification-service/        # Kafka -> SES ticket e-mails, retry topics + DLT, send-rate throttling
├── docker/                      # multi-stage Dockerfile + local infra config (Postgres, Keycloak realm, Prometheus, Grafana)
├── docker-compose.yml           # full local stack
├── scripts/                     # build-and-push-ecr.sh, local-smoke-test.sh
├── k8s/                         # EKS: eksctl cluster, add-ons, kustomize base/overlays, WAF, OTel collector  (see k8s/README.md)
└── docs/                        # ARCHITECTURE.md, MICROSERVICE-PATTERNS.md
```

## Run locally

Prerequisites: Docker (with Compose v2), ~8 GB RAM for Docker. Optional: JDK 21 + Maven 3.9 to build outside Docker; `curl`, `jq` for the smoke test.

The checked-in Keycloak users, passwords, database credentials, and Compose signing key are for local development only. Do not reuse them in production or expose the local stack to untrusted networks. Production must provide a unique `TICKET_SIGNING_KEY` (at least 32 characters) through the configured secret manager; the booking service has no default signing key.

```bash
docker compose up -d --build          # builds all images and starts infra + services
docker compose ps                     # wait until services are up (~1-2 min first time)
./scripts/local-smoke-test.sh         # admin creates a show, alice books seats, fake payment, ticket e-mail, 429 demo
```

| URL | What |
|---|---|
| http://localhost:8080 | API gateway (all `/api/v1/**` traffic) |
| http://localhost:8180 | Keycloak (admin/admin) - realm `bmtickets`, users `alice/alice123` (USER), `admin/admin123` (ADMIN) |
| http://localhost:8761 | Eureka dashboard |
| http://localhost:16686 | Jaeger traces |
| http://localhost:9090 / :3001 | Prometheus / Grafana |

Grafana provisions the **BookMyTickets - Local Service Health** dashboard automatically. It includes
request throughput and latency, response failures by service/status, top failing paths, circuit
breaker and database-pool signals, JVM heap/non-heap/CPU/threads/GC, and disk usage.
Select a time range, reproduce a failing request, and use Jaeger at `http://localhost:16686` to
inspect its distributed trace. Prometheus scrapes the gateway, discovery server, and business
services every 15 seconds; short-window rates may take a minute to become useful. CPU, JVM memory,
and disk panels are metrics from those Java services, not Windows host-wide metrics.

Get a token manually:
```bash
curl -s -X POST http://localhost:8180/realms/bmtickets/protocol/openid-connect/token \
  -d grant_type=password -d client_id=bmtickets-web -d username=alice -d password=alice123 | jq -r .access_token
```

Use Stripe test mode instead of the fake provider:
```bash
PAYMENT_PROVIDER=stripe STRIPE_API_KEY=sk_test_... STRIPE_WEBHOOK_SECRET=whsec_... docker compose up -d payment-service
stripe listen --forward-to localhost:8080/api/v1/payments/webhooks/stripe
```

Build & test without Docker: `mvn clean verify`

## Public API (via gateway)

| Method & path | Auth | Description |
|---|---|---|
| `GET /api/v1/catalog/events?city=Mumbai` | public | upcoming events in a city (cached) |
| `GET /api/v1/catalog/events/{id}` · `GET /api/v1/catalog/shows/{id}` | public | event details & shows / show with prices |
| `POST /api/v1/catalog/venues|events|shows` | ADMIN | manage catalog (`shows` also defines the seat layout) |
| `GET /api/v1/shows/{showId}/seats` | public | live seat map |
| `POST /api/v1/bookings` + header `Idempotency-Key` | USER | hold seats + create payment -> `{id, status: PENDING_PAYMENT, paymentClientSecret, expiresAt}` |
| `GET /api/v1/bookings/{id}` · `GET /api/v1/bookings` | owner/ADMIN | booking status (CONFIRMED includes `ticketCode`) / my bookings |
| `POST /api/v1/bookings/{id}/cancel` | owner | cancel an unpaid booking |
| `POST /api/v1/payments/webhooks/stripe` | Stripe signature | payment provider callbacks |

Swagger UI per service: `http://<service>:<port>/swagger-ui.html` (disabled in prod).

## Deploy to AWS

```bash
AWS_REGION=ap-south-1 AWS_ACCOUNT_ID=123456789012 ./scripts/build-and-push-ecr.sh   # images -> ECR
eksctl create cluster -f k8s/cluster/eksctl-cluster.yaml && ./k8s/cluster/install-addons.sh
kubectl apply -k k8s/overlays/prod
```
See [k8s/README.md](k8s/README.md) for the managed services to provision (Aurora, ElastiCache, MSK, Cognito, Secrets Manager, SES, WAF) and the placeholders to fill in.
