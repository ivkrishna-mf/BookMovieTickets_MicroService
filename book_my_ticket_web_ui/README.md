# BookMyTickets Web UI

The customer-facing web app for the BookMyTickets platform. Users browse movies and live events by city, pick seats on a live 3D seat map, pay, and get an e-ticket with a QR code. Admins manage the catalog from the same app.

Every screen reads and writes through the platform's microservices via the **API gateway**. The app contains no mock or hard-coded data.

React 18 · TypeScript (strict) · Vite 5 · TanStack Query 5 · React Router 6 · Framer Motion 11 · Vitest + Testing Library + MSW · nginx (non-root) container.

---

## Contents

1. [Features](#features)
2. [How the UI uses the microservices](#how-the-ui-uses-the-microservices)
3. [Run locally](#run-locally)
4. [Tests and quality checks](#tests-and-quality-checks)
5. [Configuration](#configuration)
6. [Project structure](#project-structure)
7. [Troubleshooting](#troubleshooting)
8. [Deployment](#deployment) (full guide: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md))

---

## Features

### Tabs

| Tab | Route | What it does |
|---|---|---|
| **Home** | `/` | 3D cover-flow carousel of featured titles, category shortcuts, "Now showing" and "Live events" rails for the selected city |
| **Movies** | `/movies` | Searchable movie grid with language filter chips |
| **Events** | `/events` | Concerts, Sports, Comedy, Theatre and More as category tabs, plus search |
| **My Bookings** | `/bookings` | Your bookings with status, filters (Upcoming, Pending, Past) and pagination |
| **Admin** | `/admin` | ADMIN role only. Create venues, events and shows (with a seat-layout and pricing editor), and cancel shows |

Other screens: **event details** (`/events/:id`, with date strip and showtimes grouped by venue), **seat selection** (`/shows/:id`), **checkout and e-ticket** (`/bookings/:id`), the OIDC callback, and a 404 page.

### Booking flow

1. Pick a city in the header (the choice is remembered), then pick an event and a showtime.
2. The seat map refreshes every 10 seconds. If someone else takes a seat you selected, it is dropped and you get a notice. The per-booking seat limit comes from the show.
3. **Book now** sends `POST /api/v1/bookings` with an **Idempotency-Key**. The key is reused for retries of the same show and seat set, so a double click or network retry can never create two bookings.
   - A 409 `SEATS_UNAVAILABLE` response highlights the seats that were taken and releases them.
   - A 429 response shows the `Retry-After` wait.
4. Checkout shows a countdown ring for the seat hold. You pay with the fake provider locally or Stripe's Payment Element in AWS. The page polls the booking until the saga marks it `CONFIRMED`.
5. On confirmation, the ticket card flips in 3D to reveal a QR code of the signed `ticketCode`.

If you are not signed in, your seat selection is saved across the sign-in redirect.

### Look and motion

- **Dark theme:** glass panels, gradient accents, animated aurora background over a scrolling 3D perspective grid.
- **3D depth:** cards tilt with the pointer and show a moving glare, the seat map sits on a tilted auditorium floor facing a curved glowing screen, and seats lift when hovered or selected.
- **Transitions:** spring-physics page transitions and an animated tab indicator.
- **Small touches:** a 3D cube loading spinner, a confetti burst on confirmation, and generated poster art for each event (the catalog has no images).
- **Accessibility:** all animation respects the OS "reduce motion" setting. The app has keyboard focus styles, ARIA roles and labels (for example, every seat is a labelled toggle button) and a skip link.
- **Layout:** desktop top tabs collapse to a mobile bottom tab bar.

### Production concerns

- **Sign-in:** OIDC Authorization Code + PKCE (S256), with no client secret in the browser.
  - Tokens are kept in `sessionStorage`. The access token is refreshed (one refresh at a time) before it expires.
  - The `state` parameter is checked, and post-login redirects are limited to paths inside the app (no open redirects).
- **Errors:** RFC 7807 `problem+json` errors are parsed into typed `ApiError`s.
  - Only transient failures are retried: network errors, 502, 503 and 504, including the gateway's circuit-breaker fallback.
  - `Retry-After` is honoured.
- **Code splitting:** seat selection, checkout (Stripe, QR code), My Bookings and Admin load on demand.
- **Container:** non-root nginx with a read-only root filesystem. It sends strict security headers (CSP, frame-ancestors none, nosniff), caches hashed assets as immutable, and never caches `index.html` or `config.js`.
- **One image for all environments:** the browser config is generated from environment variables when the container starts.

---

## How the UI uses the microservices

```
Browser ──► /api/**  (same origin)
   │        dev:  Vite proxy  ─┐
   │        compose: nginx    ─┼──► API Gateway :8080 ──► catalog-service       (events, shows, admin writes)
   │        EKS:  ALB path rule┘    (JWT, rate limits,  ──► seat-inventory-service (live seat map)
   │                                circuit breakers)   ──► booking-service       (saga, idempotency)
   │                                                    ──► payment-service       (dev webhook / Stripe)
   └──► OIDC: Keycloak (local) / Amazon Cognito (AWS) — Authorization Code + PKCE
```

| UI action | Gateway endpoint | Service | Auth |
|---|---|---|---|
| Home, Movies, Events | `GET /api/v1/catalog/events?city=&page=&size=` | catalog | public |
| Event details | `GET /api/v1/catalog/events/{id}` | catalog | public |
| Seat page header and prices | `GET /api/v1/catalog/shows/{id}` | catalog | public |
| Live seat map | `GET /api/v1/shows/{id}/seats` | seat-inventory | public |
| Book now | `POST /api/v1/bookings` + `Idempotency-Key` | booking | USER |
| Checkout status (polled) | `GET /api/v1/bookings/{id}` | booking | owner |
| Cancel and release seats | `POST /api/v1/bookings/{id}/cancel` | booking | owner |
| My Bookings | `GET /api/v1/bookings?page=&size=` | booking | USER |
| Pay (local) | `POST /api/v1/payments/dev/bookings/{id}/succeed` | payment (fake provider) | USER |
| Pay (AWS) | Stripe Payment Element with `paymentClientSecret`; Stripe's webhook goes to payment-service | payment | Stripe |
| Admin | `POST /api/v1/catalog/venues`, `/events`, `/shows`, `/shows/{id}/cancel` | catalog | ADMIN |

The catalog has no "list cities" endpoint, so the city list comes from runtime config (`cities`).

---

## Run locally

### Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| Node.js | 20.11 or later (tested on 22 and 24) | dev server, tests, seed script |
| Docker Desktop and Compose v2 | about 8 GB of RAM for Docker | the backend stack |

> **Port 3000 is required.** The Keycloak `bmtickets-web` client only allows redirects to `http://localhost:3000/*`, and the gateway's CORS default only allows that origin. Both the dev server and the container use port 3000 on the host.

### Option A: dev server with hot reload, backend in Docker (recommended for UI work)

From the repository root:

```bash
# 1. Start the platform (infra + microservices). The first build takes a few minutes.
docker compose up -d --build
docker compose ps                     # wait until api-gateway and the services are up (about 1-2 minutes)

# 2. Install, seed demo data, run the UI
cd book_my_ticket_web_ui
npm ci
npm run seed                          # venues, events and about 150 shows in Mumbai, Delhi and Bengaluru
npm run dev                           # http://localhost:3000
```

`npm run dev` proxies `/api/**` to `http://localhost:8080`. To point at another gateway, set `GATEWAY_URL=http://host:port npm run dev`.

### Option B: everything in Docker (production-like)

From the repository root:

```bash
docker compose -f docker-compose.yml -f book_my_ticket_web_ui/deploy/docker-compose.web.yml up -d --build
cd book_my_ticket_web_ui && npm ci && npm run seed     # once, to load demo data
```

Open http://localhost:3000. nginx serves the built app and proxies `/api` to `api-gateway:8080` inside the Compose network.

### Option C: production build preview against a running gateway

```bash
npm run build && npm run preview      # serves dist/ on :3000 with the same /api proxy
```

### Sign in

| User | Password | Roles | Can do |
|---|---|---|---|
| `alice` | `alice123` | USER | browse, book, pay, view own bookings |
| `admin` | `admin123` | USER, ADMIN | everything above, plus the **Admin** tab |

These are local-only demo credentials from `docker/keycloak/bmtickets-realm.json`.

### Demo walkthrough

1. Open **Movies** and pick *Interstellar (Re-release)*. Choose a date and a showtime.
2. Select a few seats and click **Sign in to book**. Sign in as `alice`. Your seats are still selected when you come back.
3. Click **Book now**. On checkout, click **Pay ₹…** (the fake provider simulates Stripe's webhook). A few seconds later the ticket appears. Tap it to flip to the QR code.
4. Open **My Bookings** to see the booking.
5. To see a seat conflict, open the same show in a private window as `admin` and try to book the seats Alice just took.

`npm run seed` reads these environment variables: `GATEWAY_URL` (default `http://localhost:8080`), `KEYCLOAK_URL` (`http://localhost:8180`), `ADMIN_USER`, `ADMIN_PASSWORD`, `SEED_DAYS` (default 4). Each run adds new data; it does not de-duplicate.

---

## Tests and quality checks

```bash
npm test               # all tests (Vitest, jsdom), about 10 seconds
npm run test:watch     # watch mode
npm run test:coverage  # coverage report in coverage/ (HTML + lcov)
npm run typecheck      # tsc strict
npm run lint           # ESLint (typescript-eslint + react-hooks)
npm run build          # type-check + production bundle
```

The suite has 70 tests in 4 files. They render the **real app**: routes, providers, the API client and the auth layer. Only the network is replaced, by [MSW](https://mswjs.io) handlers that copy the gateway's JSON contracts.

| File | Covers |
|---|---|
| `src/lib/lib.test.ts` | money, date and duration formatting; seat grouping, pricing, the max-seat limit and reconciliation; category tabs and search; idempotency-key reuse per intent; deterministic poster art |
| `src/api/api.test.ts` | problem+json → `ApiError`, the 409 extras (`unavailableSeats`), 429 with `Retry-After`, the 503 fallback treated as transient, network errors, normalising both Spring page JSON shapes, bearer token and `Idempotency-Key` headers, public calls sent without a token, the 401 hook |
| `src/auth/oidc.test.ts` | the PKCE S256 challenge (checked against Node's crypto), Keycloak and Cognito role claims, open-redirect protection, `state` (CSRF) checking, code exchange, refresh-token handling, config normalisation |
| `src/app.test.tsx` | the tabs and role-based Admin tab, home rails, changing city, gateway-down error and retry, movie search and language filter, event category tabs, showtimes by city, venue and date, **booking end to end** (seats → price → `POST` with `Idempotency-Key` → checkout), the 409 conflicts (`SEATS_UNAVAILABLE` releases seats, `ACTIVE_BOOKING_EXISTS` goes to the booking in progress), the 429 message, **pay → saga polling → e-ticket with QR**, cancelling, an expired hold, My Bookings filters, the Admin guard, venue creation, the show payload builder |

The Docker build runs lint and the tests before it builds the bundle, so a failing test fails the image build. Use `--build-arg SKIP_TESTS=true` only for local experiments.

---

## Configuration

Runtime config is loaded from `/config.js` before the app starts, as `window.__BMT_CONFIG__`.

- **Dev server:** edit [public/config.js](public/config.js).
- **Container:** set environment variables. [docker/40-runtime-config.sh](docker/40-runtime-config.sh) writes the file at startup.

| Container env var | `config.js` key | Default | Notes |
|---|---|---|---|
| `BMT_API_BASE_URL` | `apiBaseUrl` | `""` | `""` means the same origin (recommended). Otherwise the gateway's full URL, which then needs gateway CORS |
| `BMT_AUTH_URL` | `authUrl` | `http://localhost:8180/realms/bmtickets` | OIDC issuer. Cognito: `https://cognito-idp.<region>.amazonaws.com/<poolId>` |
| `BMT_CLIENT_ID` | `clientId` | `bmtickets-web` | public SPA client (no secret) |
| `BMT_PAYMENT_MODE` | `paymentMode` | `fake` | `fake` (local dev webhook) or `stripe` |
| `BMT_STRIPE_PUBLISHABLE_KEY` | `stripePublishableKey` | `""` | `pk_test_…` or `pk_live_…`, only when `stripe` |
| `BMT_DEFAULT_CITY` | `defaultCity` | `Mumbai` | |
| `BMT_CITIES` | `cities` | `Mumbai,Delhi,…` | comma-separated |

These variables configure nginx only:

| Env var | Default | Notes |
|---|---|---|
| `API_UPSTREAM` | `http://api-gateway:8080` | where nginx proxies `/api/`. It is resolved per request, so nginx starts even if the gateway is down |
| `CSP_CONNECT_SRC` | `http://localhost:8180 https://api.stripe.com` | extra `connect-src` origins: the identity provider and Stripe, plus the API origin if it is not the same origin |
| `NGINX_RESOLVER` | taken from `/etc/resolv.conf` | DNS server nginx uses for `API_UPSTREAM` |

---

## Project structure

```
book_my_ticket_web_ui/
├── src/
│   ├── api/            http.ts (fetch wrapper, ApiError, problem+json), index.ts (typed endpoint clients), types.ts (DTOs)
│   ├── auth/           oidc.ts (PKCE flow, tokens, refresh, logout), AuthProvider.tsx
│   ├── components/     Layout (tabs), HeroCarousel, EventCard, Poster, SeatMap, TicketCard, StripePayment, States…
│   ├── hooks/          queries.ts (TanStack Query hooks), city & toast contexts, useTilt, useCountdown
│   ├── lib/            pure logic: seats, categories, format, poster, idempotency
│   ├── pages/          Home, Browse, EventDetails, SeatSelection, Checkout, MyBookings, Admin, AuthCallback, NotFound
│   ├── styles/         global.css (design tokens, 3D scenes, responsive layout)
│   ├── test/           setup, MSW server and fixtures, renderApp helper
│   ├── App.tsx         providers, routes (lazy-loaded), error boundary, query client policy
│   └── config.ts       runtime config loader
├── public/             config.js (dev runtime config), favicon.svg
├── scripts/            seed-demo-data.mjs
├── nginx/              default.conf.template, security-headers.inc.template
├── docker/             entrypoint hooks: DNS resolver, runtime config
├── deploy/             docker-compose.web.yml, k8s/ (Kustomize for EKS)
├── docs/DEPLOYMENT.md
└── Dockerfile          node build stage (lint + tests + build) → nginx-unprivileged runtime
```

---

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| "Cannot reach BookMyTickets…" | The gateway is not running on :8080. Run `docker compose ps` and `docker compose logs api-gateway`. Services need about 1-2 minutes to register with Eureka after starting |
| "Nothing playing in Mumbai yet" | The catalog is empty. Run `npm run seed`, or create a show in the **Admin** tab |
| A show has no seats right after seeding | Seat maps are built asynchronously (Kafka `ShowScheduled` → seat-inventory). Wait a few seconds and refresh |
| Keycloak says "Invalid parameter: redirect_uri" | The app is not on `http://localhost:3000`. Use port 3000, or add your origin to the `bmtickets-web` client's redirect URIs and web origins |
| Sign-in loops or "state mismatch" | You started sign-in in another tab, or `sessionStorage` was cleared. Sign in again from this tab |
| "You're going a little fast…" | The gateway rate limiter returned 429 (the booking bucket allows 2 per second with a burst of 5 per user). Wait for the time shown |
| The Admin tab is missing | Sign in as `admin`. The tab needs the `ADMIN` realm role |
| Payment never confirms locally | Check that `payment-service` runs with `PAYMENT_PROVIDER=fake` (the default), and look at `docker compose logs booking-service payment-service` |

---

## Deployment

The short version for AWS (EKS, same cluster as the platform):

```bash
docker build -t bmtickets/web-ui:$(git rev-parse --short HEAD) book_my_ticket_web_ui
# push to ECR, then:
cd book_my_ticket_web_ui/deploy/k8s && kustomize edit set image bmtickets/web-ui=<ecr-repo>:<tag>
kubectl apply -k book_my_ticket_web_ui/deploy/k8s
```

See **[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)** for the full guide: ECR, Cognito app client, ALB routing so the browser uses one origin, gateway CORS, Stripe, the S3 + CloudFront alternative, a CI pipeline, rollout and rollback, and a go-live checklist.
