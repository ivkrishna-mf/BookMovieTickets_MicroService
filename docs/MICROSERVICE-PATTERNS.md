# BookMyTickets - Microservice Patterns Catalogue

Every pattern used in the platform, explained as **Problem -> How it is implemented here (with code pointers) ->
Benefit -> Trade-off**. Paths are relative to the repository root.

| # | Category | Patterns |
|---|---|---|
| A | Decomposition & data | 1 Decompose by business capability · 2 Database per service · 3 Shared kernel library |
| B | Communication | 4 API Gateway · 5 Service registry & discovery · 6 Client-side load balancing · 7 Remote procedure invocation (gRPC, Feign) · 8 Event-driven messaging · 9 Domain events vs commands · 10 Webhook receiver |
| C | Data consistency & transactions | 11 Saga (orchestration) · 12 Compensating transaction · 13 Transactional outbox · 14 Idempotent consumer (inbox) · 15 Idempotency key · 16 Reservation (seat hold with TTL) · 17 Distributed lock · 18 Pessimistic & optimistic locking · 19 Local transactions, no 2PC · 20 Reconciliation |
| D | Resilience | 21 Timeout · 22 Retry with back-off & jitter · 23 Circuit breaker · 24 Fallback / graceful degradation · 25 Bulkhead · 26 Rate limiter · 27 Throttling & load shedding · 28 Retry topics & dead-letter queue · 29 Health check API |
| E | Security | 30 Access token (JWT) · 31 Zero-trust service identity · 32 Anti-corruption layer |
| F | Performance | 33 Cache-aside · 34 CQRS-lite read path · 35 Micro-caching |
| G | Observability | 36 Distributed tracing · 37 Log aggregation & correlation · 38 Application metrics · 39 Audit trail |
| H | Deployment & operations | 40 Service instance per container · 41 Externalized configuration · 42 Zero-downtime rolling deploy · 43 Autoscaling (HPA, KEDA) · 44 Singleton scheduled job (ShedLock) · 45 Competing consumers · 46 Twelve-Factor App |

---

## A. Decomposition & data

### 1. Decompose by business capability
* **Problem** - A monolith couples browsing (read-heavy, cacheable) with booking (write-heavy, contended) and payments (regulated, external); they can't scale or deploy independently.
* **Implementation** - Six services aligned to capabilities and bounded contexts: `catalog-service`, `seat-inventory-service`, `booking-service`, `payment-service`, `notification-service`, plus the edge `api-gateway`.
* **Benefit** - Independent scaling (inventory and gateway scale far beyond payment), independent releases, smaller blast radius, team ownership.
* **Trade-off** - Distributed-systems complexity (network failures, eventual consistency) - addressed by the patterns below.

### 2. Database per service
* **Problem** - A shared database creates hidden coupling (schema changes break other teams) and a single scaling bottleneck.
* **Implementation** - Each service owns its schema and Flyway migrations (`*/src/main/resources/db/migration`): `catalog_db`, `inventory_db`, `booking_db`, `payment_db`, `notification_db` (`docker/postgres/init.sql`; separate Aurora clusters / databases in AWS). No service reads another's tables - data is obtained through APIs or events (e.g. booking stores a snapshot of event title/venue taken from catalog via gRPC).
* **Benefit** - Loose coupling, per-service tuning and scaling, failure isolation.
* **Trade-off** - No cross-service joins or ACID transactions -> Saga + Outbox.

### 3. Shared kernel library (used sparingly)
* **Problem** - Cross-cutting plumbing (security config, error format, outbox) would otherwise be copy-pasted and drift.
* **Implementation** - `common-lib` holds only technical building blocks and integration-event contracts (`common-lib/.../events`). Protobuf contracts live in `grpc-api`. No domain logic is shared.
* **Benefit** - Consistency (same RFC 7807 errors, same JWT mapping) and less boilerplate.
* **Trade-off** - A library change requires rebuilding dependents; kept small and versioned to avoid a "distributed monolith".

---

## B. Communication

### 4. API Gateway
* **Problem** - Clients shouldn't know internal topology, and every service shouldn't re-implement TLS, auth, rate limiting, CORS.
* **Implementation** - `api-gateway` (Spring Cloud Gateway, reactive Netty) behind ALB + WAF. Routes declared in code (`RoutesConfig`), each with its own rate-limit key/bucket, circuit breaker, retry and request-size limit; JWT validation in `SecurityConfig`; `LoadSheddingFilter`; `FallbackController`.
* **Benefit** - Single, policy-enforcing entry point; services stay focused on business logic; internal refactoring invisible to clients.
* **Trade-off** - Extra hop (~1 ms) and a critical component - run >= 3 replicas across AZs with HPA and PDB.

### 5. Service registry & discovery
* **Problem** - Pod IPs change constantly; callers need to find healthy instances.
* **Implementation** -
  * **Local/docker**: Netflix **Eureka** (`discovery-server`) - client-side discovery; services self-register (`eureka.client.*` in `bmtickets-defaults.yml`).
  * **EKS**: the **Kubernetes API is the registry** (Spring Cloud Kubernetes LoadBalancer, `SERVICE` mode; RBAC in `k8s/base/rbac-discovery.yaml`). `EUREKA_CLIENT_ENABLED=false` in `platform-config`.
  * The application code is identical in both: `lb://booking-service`, `@FeignClient(name = "payment-service")`, gRPC `discovery:///` locally / `dns:///` headless Service on EKS.
* **Benefit** - On EKS no extra registry to operate/scale/secure, health is driven by readiness probes (single source of truth); locally the same code runs on a laptop.
* **Trade-off** - Two mechanisms to understand; mitigated by the `DiscoveryClient`/`LoadBalancer` abstraction.

### 6. Client-side load balancing
* **Problem** - Long-lived HTTP/2 (gRPC) connections defeat kube-proxy's per-connection balancing: one pod would get all traffic.
* **Implementation** - Spring Cloud LoadBalancer for Feign/gateway; gRPC `default-load-balancing-policy: round_robin` over a **headless Service** that returns all pod IPs (`k8s/base/services/*-grpc`).
* **Benefit** - Even load across pods, faster failover.
* **Trade-off** - DNS refresh delay on scale-out (seconds); keep-alive settings tuned in `application.yml`.

### 7. Remote procedure invocation - gRPC and OpenFeign
* **Problem** - The booking saga needs immediate answers from catalog (prices) and inventory (hold result) and the payment `clientSecret`.
* **Implementation** -
  * **gRPC** contracts in `grpc-api/src/main/proto` (`CatalogQueryService`, `SeatInventoryService`); servers `CatalogGrpcService`, `InventoryGrpcService`; clients `CatalogClient`, `InventoryClient` with per-call **deadlines**.
  * **OpenFeign** `PaymentClient` (declarative REST) wrapped by `PaymentServiceAdapter` for resilience.
* **Benefit** - gRPC: compact protobuf, HTTP/2 multiplexing, strict typed contracts, deadlines propagate. Feign: readable declarative REST for a small, also human-consumed API.
* **Trade-off** - Synchronous coupling (availability multiplies) - limited to calls the user must wait for, protected by timeouts/retries/circuit breakers.

### 8. Event-driven messaging (publish/subscribe)
* **Problem** - Work that doesn't need to block the user (seat-map generation, e-mails, payment outcomes) shouldn't be coupled in time.
* **Implementation** - Kafka topics (`common-lib/.../events/Topics.java`): `catalog.show-events`, `payment.events`, `payment.commands`, `booking.events`. Key = aggregate id (per-booking ordering). Producers: idempotent, `acks=all`. Consumers: `ShowEventsListener`, `PaymentEventsListener`, `PaymentCommandsListener`, `BookingEventsListener`.
* **Benefit** - Temporal decoupling (consumers can be down), natural buffering of spikes, replay, easy to add new consumers (analytics, fraud) without touching producers.
* **Trade-off** - Eventual consistency, at-least-once delivery -> idempotent consumers; more infra (MSK).

### 9. Domain events vs commands
* **Problem** - Not every message is a broadcast fact; some are requests addressed to one owner.
* **Implementation** - **Events** (past tense, many consumers): `ShowScheduled`, `PaymentSucceeded`, `BookingConfirmed`... **Command** (imperative, one owner): `RefundRequested` on `payment.commands`. Type carried in the `eventType` Kafka header; payloads are versionable JSON records.
* **Benefit** - Clear ownership and intent; producers don't need to know consumers of events.
* **Trade-off** - Contract evolution discipline (additive changes only, or new event types).

### 10. Webhook receiver
* **Problem** - Payment completion happens between the user's browser and Stripe (3-DS); we learn about it asynchronously.
* **Implementation** - `WebhookController` (public path, per-IP gateway limit) -> `StripePaymentGateway.parseWebhook` verifies the **HMAC signature + timestamp** -> `PaymentService.handleProviderEvent` de-duplicates by Stripe event id and applies state-guarded transitions; returns 2xx fast.
* **Benefit** - Reliable, secure integration; Stripe retries for up to 3 days if we're down.
* **Trade-off** - Must be idempotent and order-tolerant (events can arrive late or twice) - handled by guards.

---

## C. Data consistency & transaction management

### 11. Saga - orchestration
* **Problem** - A booking spans three services/databases (seats, booking, payment); a distributed ACID transaction (2PC/XA) doesn't scale and isn't supported by Kafka/Stripe.
* **Implementation** - `booking-service` is the **orchestrator**:
  * synchronous leg `BookingSagaOrchestrator.createBooking` - GetShow -> persist `PENDING_SEATS` -> HoldSeats -> create PaymentIntent -> `PENDING_PAYMENT`;
  * asynchronous leg `BookingCompletionService` driven by `PaymentEventsListener` - ConfirmSeats -> `CONFIRMED` -> `BookingConfirmed`;
  * timeout handling `BookingExpiryJob`.
  The state machine is explicit in `BookingStatus` / `Booking` (transitions guarded, `@Version`).
* **Benefit** - Each step is a local transaction; the flow is visible in one place (easier to reason about than choreography for a 4+ step business process); failures are handled deterministically.
* **Trade-off** - Orchestrator is a central point of logic (kept stateless, state in DB); intermediate states are visible (`PENDING_PAYMENT`).

### 12. Compensating transaction
* **Problem** - When a later saga step fails, earlier steps must be semantically undone.
* **Implementation** - Release seats (`InventoryClient.release`) when payment creation fails, payment fails or the booking expires; **refund** (`RefundRequested` command -> `PaymentService.refund`) when money was captured but the booking can't be honoured (expired, seats lost); cancel PaymentIntent on expiry/user cancel.
* **Benefit** - Business-level consistency without distributed locks.
* **Trade-off** - Compensations must themselves be idempotent and retryable (they are: keyed by bookingId; safety nets: Redis TTL + inventory sweeper).

### 13. Transactional outbox
* **Problem** - "Update DB then publish to Kafka" is a dual write: a crash between them loses the event or publishes an event for a rolled-back change.
* **Implementation** - `OutboxService.record(...)` (propagation `MANDATORY`) inserts into `outbox_event` **in the same local transaction** as the state change; `OutboxRelay` polls with `SELECT ... FOR UPDATE SKIP LOCKED`, publishes with broker ack and marks rows published; old rows purged. Used by catalog, booking, payment.
* **Benefit** - Atomicity between state and events; replicas relay in parallel without leader election.
* **Trade-off** - At-least-once (duplicates possible) and small latency (poll interval 250 ms); for very high volume swap the relay for Debezium CDC.

### 14. Idempotent consumer (inbox)
* **Problem** - Kafka and webhooks deliver at least once; reprocessing must not confirm twice, refund twice or e-mail twice.
* **Implementation** - `InboxGuard.firstDelivery(consumer, eventId)` inserts into `processed_event` inside the business transaction (PK collision on concurrent duplicates); event id = outbox row id (Kafka header) or Stripe event id. Plus natural idempotency via state guards (`markSucceeded()` returns false if already succeeded) and unique `notification_log(booking_id, type)`.
* **Benefit** - Effectively-once processing on top of at-least-once delivery.
* **Trade-off** - Extra table/insert per message; purge old rows periodically.

### 15. Idempotency key (API)
* **Problem** - Clients retry on timeouts and users double-click "Pay"; without protection that creates duplicate bookings.
* **Implementation** - Mandatory `Idempotency-Key` header on `POST /api/v1/bookings` (`BookingController`). `IdempotencyService`: Redis `SET NX` lock (`IN_PROGRESS`, 60 s) -> execute -> store `COMPLETED` + response (24 h); request fingerprint (SHA-256 of show + sorted seats) -> 422 on key reuse with a different body; concurrent duplicate -> 409; replay returns the original 201 with `Idempotent-Replayed: true`. Durable backstop: DB unique `(user_id, idempotency_key)`. Downstream idempotency: `holdRef=bookingId`, unique `payment.booking_id`, Stripe idempotency keys.
* **Benefit** - Safe retries end to end; the same intent is executed at most once.
* **Trade-off** - Clients must generate and persist keys per intent (documented contract).

### 16. Reservation (seat hold with TTL)
* **Problem** - Seats must be exclusively reserved while the user pays, but abandoned carts must not lock seats forever.
* **Implementation** - Seat state machine `AVAILABLE -> HELD(holdRef, expiresAt) -> BOOKED` (`Seat`), Redis keys with PX TTL (`hold_seats.lua`), booking payment window (8 min) shorter than hold TTL (10 min), `HoldExpirySweeper` safety net.
* **Benefit** - Fair, time-boxed exclusivity; seats return automatically.
* **Trade-off** - Tuning windows: too short frustrates payers, too long reduces sell-through.

### 17. Distributed lock (Redis)
* **Problem** - 50k users clicking the same seat would all queue on the same Postgres row locks.
* **Implementation** - `SeatHoldCache.tryHold`: atomic Lua acquires **all** requested seats or none, owner = bookingId, auto-expiring; compare-and-delete release (`release_seats.lua`). `{showId}` hash tag keeps all keys in one Redis Cluster slot.
* **Benefit** - Contention resolved in ~1 ms in memory; the DB only sees likely winners.
* **Trade-off** - Redis isn't the source of truth (async replication) - correctness comes from the DB layer (#18); if Redis is down we degrade to DB-only locking.

### 18. Pessimistic & optimistic locking
* **Problem** - Concurrent transitions on the same rows (two holds on a seat; payment-success vs expiry on a booking).
* **Implementation** - **Pessimistic**: `SeatRepository.lockSeats` = `SELECT ... ORDER BY seat_number FOR UPDATE` (deterministic order => no deadlocks; `lock_timeout = 3s`). **Optimistic**: `@Version` on `Seat`, `Booking`, `Payment`, `Show`. **Constraints**: unique `(show_id, seat_number)`, unique `(user_id, idempotency_key)`, partial unique "one active booking per user+show", unique `payment.booking_id`.
* **Benefit** - Database-guaranteed correctness; constraints are the last line of defence even against application bugs.
* **Trade-off** - Lock waits under extreme contention - bounded by Redis + admission throttling.

### 19. Transaction management: local transactions, no 2PC
* **Problem** - Keep each service consistent without distributed transactions or long-held DB connections.
* **Implementation** - `@Transactional` for simple use cases; `TransactionTemplate` in sagas so **remote calls happen outside transactions** (no DB connection held while waiting on gRPC/Stripe); `Propagation.MANDATORY` on `OutboxService`/`InboxGuard` to enforce "same transaction as the business change"; `open-in-view=false`; `READ COMMITTED` + explicit locks where needed.
* **Benefit** - Short transactions, high throughput, clear consistency boundaries.
* **Trade-off** - Developers must reason about which work is inside which transaction - made explicit in code.

### 20. Reconciliation
* **Problem** - Despite retries, an event can end up on a DLT, leaving money captured but a booking pending.
* **Implementation** - `BookingCompletionService.expire`: before expiring, the PaymentIntent is cancelled; if the PSP says `ALREADY_SUCCEEDED` the saga **completes the booking itself** (idempotent) instead of expiring. Payment-service `cancel` likewise emits `PaymentSucceeded` if it discovers a success the webhook didn't deliver yet.
* **Benefit** - Self-healing; no customer pays without receiving tickets (or a refund).
* **Trade-off** - Extra PSP call at expiry time (rare path).

---

## D. Resilience

### 21. Timeout
* **Implementation** - gRPC deadlines (`withDeadlineAfter` 1.5-3 s), Feign connect/read 2 s/5 s, gateway Netty connect 2 s / response 20 s (`ResilienceConfig`), Stripe 3 s/10 s, Hikari connection timeout 3 s, Postgres `lock_timeout` 3 s, Redis 500 ms.
* **Benefit** - Slow dependencies can't exhaust threads/connections; failures surface quickly.

### 22. Retry with exponential back-off & jitter
* **Implementation** - Resilience4j `@Retry` on `CatalogClient`, `InventoryClient`, `PaymentServiceAdapter` (3 attempts, 200 ms x2, randomized 50%); business exceptions and open-circuit errors are never retried. Gateway retries idempotent GETs only. Stripe SDK network retries with idempotency keys. Kafka: blocking back-off (`KafkaErrorHandlingConfig`) or non-blocking retry topics (#28).
* **Benefit** - Transparent recovery from transient faults; jitter prevents synchronized retry storms.
* **Trade-off** - Only safe for idempotent operations - which is why every command is idempotent.

### 23. Circuit breaker
* **Implementation** - Gateway per-route breakers (`ReactiveResilience4JCircuitBreakerFactory` defaults in `ResilienceConfig`); `@CircuitBreaker` on catalog/inventory/payment clients and the Stripe adapter (`resilience4j.circuitbreaker.*` in YAML: 50% failure or 80% slow over 50 calls, open 10-20 s, half-open probes). Business errors are ignored by breakers.
* **Benefit** - Fail fast when a dependency is down, give it room to recover, prevent cascading failure.
* **Trade-off** - Requires sensible thresholds and dashboards (`/actuator/circuitbreakers`, health indicators).

### 24. Fallback / graceful degradation
* **Implementation** - Gateway `forward:/fallback/{service}` returns a consistent 503 problem+json; Redis outage -> `SeatHoldCache` falls back to DB locking, `IdempotencyService` relies on DB constraints, `DistributedRateLimiter` fails open; e-mail outage -> retry topics, booking unaffected.
* **Benefit** - Partial failures degrade features instead of taking the platform down.

### 25. Bulkhead
* **Implementation** - `@Bulkhead(name="createBooking")` caps concurrent sagas per pod; separate pools per dependency (Hikari per service, gRPC channels per target, Feign client per service); Kubernetes resource requests/limits isolate pods.
* **Benefit** - One overloaded flow can't consume all resources of a service.

### 26. Rate limiter
* **Implementation** (details in ARCHITECTURE.md §9):
  * Edge: AWS WAF rate-based rules per IP.
  * Gateway: Redis token bucket per user (bookings 2/s burst 5) or per IP (catalog 50/s) - `RateLimiterConfig`.
  * Service: Resilience4j `@RateLimiter` per pod; business limits via `DistributedRateLimiter` (10 booking attempts per user per show per hour; per-show hold admission 150/s cluster-wide).
* **Benefit** - Fair usage, abuse/bot resistance, protection of scarce inventory and downstream capacity.
* **Trade-off** - Limits must be tuned and monitored; shared NAT IPs make per-IP limits coarse (hence per-user keys after login).

### 27. Throttling & load shedding
* **Implementation** - Concurrency throttling: gateway `LoadSheddingFilter` (max in-flight per pod -> 503), saga bulkhead, Tomcat `accept-count`, Hikari pool size. Rate smoothing: `DistributedRateLimiter.acquire()` makes callers **wait** for Stripe (80/s global) and SES (account quota) tokens instead of being rejected by the provider. Consumer pacing: `max.poll.records`, listener concurrency, KEDA.
* **Benefit** - The system runs at its maximum sustainable throughput instead of collapsing under overload; external quotas are respected cluster-wide.
* **Trade-off** - Some requests are deliberately delayed or rejected - with explicit `Retry-After` guidance.

### 28. Retry topics & dead-letter queue
* **Implementation** - notification-service `@RetryableTopic` (non-blocking: `booking.events-retry-*` with 2 s/6 s/18 s back-off, then `booking.events.DLT`, `@DltHandler` + metric for alarms); other consumers use `DefaultErrorHandler` with exponential back-off then `<topic>.DLT`; malformed payloads skip retries.
* **Benefit** - A poison message never blocks a partition; failed work is preserved for inspection/replay.
* **Trade-off** - DLTs need monitoring and a replay runbook.

### 29. Health check API
* **Implementation** - Actuator liveness/readiness groups (`management.endpoint.health.probes.enabled`) used by Kubernetes startup/liveness/readiness probes and the ALB; readiness deliberately **not** tied to shared dependencies to avoid fleet-wide flapping.
* **Benefit** - Automatic restart of stuck pods; traffic only to ready pods; zero-downtime deploys.

---

## E. Security

### 30. Access token (JWT)
* **Implementation** - OAuth2/OIDC with Cognito (prod) / Keycloak (local). Gateway validates tokens at the edge; every service validates again (`ResourceServerSecurityConfig`). `JwtRoleConverter` maps `cognito:groups` / `realm_access.roles` to `ROLE_*` and scopes to `SCOPE_*`; `@PreAuthorize` for RBAC; ownership checks in `BookingController`.
* **Benefit** - Stateless, horizontally scalable auth; standard, IdP-agnostic.

### 31. Zero-trust service identity
* **Implementation** - booking-service obtains a **client-credentials** token (`ServiceTokenProvider`, cached & auto-renewed) attached to Feign calls (`RequestInterceptor`) and gRPC calls (`StubTransformer` + `BearerTokenCallCredentials`). `/internal/**` and all gRPC methods require the internal authority (`GrpcJwtServerInterceptor`). NetworkPolicies restrict who can even open a connection.
* **Benefit** - A compromised pod or misrouted request can't call internal APIs; works from Kafka listeners and scheduled jobs (no user context needed).

### 32. Anti-corruption layer (ports & adapters)
* **Implementation** - `PaymentGateway` port with neutral types (`GatewayIntent`, `ProviderEvent`, `CancelOutcome`); `StripePaymentGateway` adapter translates Stripe models/exceptions (`ProviderUnavailableException` vs `ProviderRejectedException`); `FakePaymentGateway` for local. Same idea for `EmailSender` (SES / logging).
* **Benefit** - Vendor specifics never leak into the domain or saga; switching or adding PSPs (Razorpay, Adyen) is an adapter.

---

## F. Performance

### 33. Cache-aside
* **Implementation** - catalog `@Cacheable` / `@CacheEvict` with Redis (`CatalogService`, `CacheConfig`): typed JSON serializers (no polymorphic deserialization risk), per-cache TTLs (2-10 min), `sync=true` against stampedes within a pod, eviction on writes.
* **Benefit** - Most reads never hit Postgres; p99 browse latency in single-digit ms.

### 34. CQRS-lite read path
* **Implementation** - Read models are shaped for queries (`EventPage`, `ShowResponse`, `SeatMapResponse`) and served from cache/CDN (`Cache-Control: public, max-age=30`), while writes go through the transactional model.
* **Benefit** - Read and write sides scale independently. A full CQRS (OpenSearch projection fed by events) is a natural next step for search.

### 35. Micro-caching
* **Implementation** - `SeatMapCache` caches each show's seat map for 2 s and evicts on hold/confirm/release.
* **Benefit** - 100k viewers of a hot show produce ~0.5 DB queries/s; correctness is unaffected because every hold is validated.

---

## G. Observability

### 36. Distributed tracing
* **Implementation** - Micrometer Tracing + OpenTelemetry bridge; automatic spans for HTTP, Feign (`feign-micrometer`), gRPC (grpc-spring-boot-starter observation), Kafka (`observation-enabled`), plus `@Observed` on the saga; OTLP -> ADOT -> X-Ray (Jaeger locally). Sampling configurable per environment.
* **Benefit** - One trace shows gateway -> booking -> gRPC inventory -> Feign payment -> Kafka -> notification; pinpoints latency and failures.

### 37. Log aggregation & correlation
* **Implementation** - Spring Boot structured logging (ECS JSON) to stdout with `traceId`/`spanId` in every line (`logging.pattern.correlation`), shipped by Fluent Bit to CloudWatch Logs.
* **Benefit** - Search all services' logs for one trace id.

### 38. Application metrics
* **Implementation** - Micrometer Prometheus registry: HTTP RED metrics with histograms, Resilience4j (circuit state, bulkhead, rate limiter), Hikari, Kafka, JVM; custom business metrics (`inventory.hold.success|conflict|throttled`, `gateway.load_shed.total`, `notification.dlt.total`). Scraped by ADOT -> Amazon Managed Prometheus -> Grafana.
* **Benefit** - SLO dashboards and alerting.

### 39. Audit trail
* **Implementation** - Outbox table (every integration event), `notification_log`, payment `last_error`, booking `failure_reason` and timestamps.
* **Benefit** - Support and dispute investigation ("why was I charged / where is my ticket?").

---

## H. Deployment & operations

### 40. Service instance per container
* **Implementation** - One parameterised multi-stage `docker/Dockerfile` (layered jars, non-root, container-aware JVM flags); multi-arch images pushed to ECR by `scripts/build-and-push-ecr.sh` with immutable tags.
* **Benefit** - Reproducible, isolated, portable deployables.

### 41. Externalized configuration
* **Implementation** - All environment-specific values are env vars with local defaults (`${ENV:default}`), shared defaults in `bmtickets-defaults.yml`, ConfigMap `platform-config`, secrets from AWS Secrets Manager via External Secrets; Kustomize overlays per environment.
* **Benefit** - Same image promoted from dev to prod; secrets never in Git/images.

### 42. Zero-downtime rolling deployment
* **Implementation** - `maxUnavailable: 0`, startup/readiness probes, `preStop` sleep + `server.shutdown=graceful`, PDBs, ALB deregistration delay, backward-compatible Flyway migrations.
* **Benefit** - Deploy during an on-sale without dropping requests.

### 43. Autoscaling
* **Implementation** - HPA on CPU (fast up / slow down) per service; **KEDA** on Kafka consumer lag for notification-service; Cluster Autoscaler/Karpenter for nodes; topology spread across 3 AZs.
* **Benefit** - Capacity follows demand automatically; cost follows usage.

### 44. Singleton scheduled job (ShedLock)
* **Implementation** - `@SchedulerLock` with a Redis lock provider on `BookingExpiryJob` and `HoldExpirySweeper` (`ShedLockConfig`).
* **Benefit** - Jobs run once per cluster, not once per replica, without a separate scheduler service.

### 45. Competing consumers
* **Implementation** - Kafka consumer groups (`groupId` per service), `concurrency` per pod, partitions = max parallelism; outbox relay replicas compete via `SKIP LOCKED`.
* **Benefit** - Horizontal scaling of asynchronous work with ordering per key.

### 46. Twelve-Factor App
See the mapping table in [ARCHITECTURE.md §14](ARCHITECTURE.md#14-twelve-factor-compliance): codebase, dependencies, config, backing services, build/release/run, stateless processes, port binding, concurrency, disposability, dev/prod parity, logs, admin processes - each implemented and referenced there.
