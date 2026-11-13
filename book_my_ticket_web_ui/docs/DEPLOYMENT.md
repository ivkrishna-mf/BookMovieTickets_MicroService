# Deploying the BookMyTickets Web UI

This guide covers deploying the web UI next to the BookMyTickets platform on **Amazon EKS** (the recommended path), and an alternative on **S3 + CloudFront**. It assumes the backend is already deployed as described in [k8s/README.md](../../k8s/README.md). That means the namespace `bmtickets`, the `api-gateway` Service, the AWS Load Balancer Controller, Cognito and WAF all exist.

## Contents

1. [Architecture](#1-architecture)
2. [Build the image](#2-build-the-image)
3. [Push to Amazon ECR](#3-push-to-amazon-ecr)
4. [Identity: Cognito app client](#4-identity-cognito-app-client)
5. [Configure and deploy on EKS](#5-configure-and-deploy-on-eks)
6. [Payments: Stripe](#6-payments-stripe)
7. [Alternative: S3 + CloudFront](#7-alternative-s3--cloudfront)
8. [CI/CD pipeline](#8-cicd-pipeline)
9. [Rollout, rollback and operations](#9-rollout-rollback-and-operations)
10. [Go-live checklist](#10-go-live-checklist)

---

## 1. Architecture

```
            Route 53: www.bmtickets.example
                         │
                 AWS WAF ─┤  (shared web ACL: OWASP rules, IP rate limits)
                         ▼
          ALB (ingress group "bmtickets", TLS 1.3, ACM cert)
             │ /api/*                      │ /*
             ▼                             ▼
     api-gateway pods               web-ui pods (nginx, non-root, read-only FS)
     (JWT, Redis rate limits,       static SPA + /config.js generated at start
      circuit breakers)             HPA 2→10, PDB, zone spread
```

**Why one origin.** The ALB routes `/api/*` on the web host straight to the gateway, and everything else to the web pods.

- The browser never makes cross-origin API calls, so there are no CORS preflights and no CORS configuration to keep in sync.
- The gateway still sees the real client IP through the ALB's `X-Forwarded-For`, so `TRUSTED_PROXY_HOPS=1` stays correct and per-IP rate limiting keeps working.
- The web pods do not proxy API traffic in production. Their nginx `/api` proxy is only used by docker compose.

**One image for every environment.** The image contains no environment-specific values. At startup, `docker/40-runtime-config.sh` writes `/tmp/config.js` from environment variables (a ConfigMap on EKS), and `nginx/*.template` are rendered by the nginx image's `envsubst` step.

---

## 2. Build the image

```bash
cd book_my_ticket_web_ui
docker build -t bmtickets/web-ui:$(git rev-parse --short HEAD) .
```

The build runs in two stages:

1. **Build** (`node:22-alpine`): `npm ci`, then lint and the 70 tests (the build fails if any fail), then `vite build`.
2. **Runtime** (`nginxinc/nginx-unprivileged:1.27-alpine`): serves the build as UID 101 on port 8080, with no shell tools added and a `HEALTHCHECK` on `/healthz`.

For multi-arch EKS node groups (Graviton), build for both architectures:

```bash
docker buildx build --platform linux/amd64,linux/arm64 -t <repo>:<tag> --push .
```

Smoke-test the image locally:

```bash
docker run --rm -p 3000:8080 bmtickets/web-ui:<tag>
curl -s localhost:3000/healthz          # ok
curl -s localhost:3000/config.js        # rendered runtime config
curl -sI localhost:3000/ | grep -i content-security-policy
```

---

## 3. Push to Amazon ECR

```bash
AWS_REGION=ap-south-1
AWS_ACCOUNT_ID=123456789012
REPO=$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/bmtickets/web-ui
TAG=$(git rev-parse --short HEAD)

aws ecr describe-repositories --repository-names bmtickets/web-ui >/dev/null 2>&1 || \
  aws ecr create-repository --repository-name bmtickets/web-ui \
    --image-tag-mutability IMMUTABLE --image-scanning-configuration scanOnPush=true

aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin ${REPO%%/*}
docker tag bmtickets/web-ui:$TAG $REPO:$TAG
docker push $REPO:$TAG
```

Use immutable tags (git SHA). Never deploy `latest`.

---

## 4. Identity: Cognito app client

The platform uses an Amazon Cognito user pool in AWS (Keycloak locally). Create a **public** app client for the SPA:

| Setting | Value |
|---|---|
| Client secret | **none** (public client; the app uses PKCE) |
| OAuth grant types | Authorization code |
| OAuth scopes | `openid`, `profile`, `email` |
| Callback URL | `https://www.bmtickets.example/auth/callback` |
| Sign-out URL | `https://www.bmtickets.example/` |
| Admin role | put admins in a Cognito group named `ADMIN`. The UI reads `cognito:groups`, and the services must map that claim to `ROLE_ADMIN` the same way they do for Keycloak realm roles |

Then:

- Set `BMT_AUTH_URL` to the **issuer** `https://cognito-idp.<region>.amazonaws.com/<userPoolId>`. The UI reads `/.well-known/openid-configuration` to find the hosted-UI `authorize` and `token` endpoints.
- Set `BMT_CLIENT_ID` to the app client ID.
- Add both the issuer origin and the hosted-UI domain (`https://<prefix>.auth.<region>.amazoncognito.com`) to `CSP_CONNECT_SRC`.

> **Sign-out.** Cognito does not publish an `end_session_endpoint`, so **Sign out** clears the local session but not the hosted-UI session cookie. For a full Cognito sign-out, add a redirect to `https://<domain>/logout?client_id=…&logout_uri=…`. Keycloak signs out completely.

---

## 5. Configure and deploy on EKS

The manifests live in [deploy/k8s/](../deploy/k8s/):

| Resource | Purpose |
|---|---|
| `ConfigMap web-ui-config` | runtime config: issuer, client ID, payment mode, cities, CSP origins |
| `Deployment web-ui` | 2+ replicas, non-root, `readOnlyRootFilesystem`, all capabilities dropped, seccomp, zone spread, readiness and liveness on `/healthz`, a 10 s `preStop` so the ALB can drain |
| `Service web-ui` | ClusterIP on 8080 (`http`) |
| `HorizontalPodAutoscaler` | 2→10 replicas at 70 % CPU |
| `PodDisruptionBudget` | at least 1 replica available during node drains |
| `NetworkPolicy allow-alb-to-web-ui` | required because the namespace denies all ingress by default |
| `Ingress bmtickets-web` | `www` host: `/api` → `api-gateway`, `/` → `web-ui`. Joins the platform ALB through `group.name: bmtickets` |

### 5.1 Fill in the placeholders

In `deploy/k8s/web-ui.yaml`:

- `BMT_AUTH_URL`, `BMT_CLIENT_ID`, `BMT_STRIPE_PUBLISHABLE_KEY`, `CSP_CONNECT_SRC`
- `certificate-arn`: an ACM certificate covering `www.bmtickets.example`
- `wafv2-acl-arn`: the same web ACL as the API ingress
- `host`: your domain
- the NetworkPolicy `cidr`: your VPC CIDR (keep it in sync with `k8s/base/network-policies.yaml`)

In `deploy/k8s/kustomization.yaml`, set the image `newName` and `newTag`, or let CI do it:

```bash
cd book_my_ticket_web_ui/deploy/k8s
kustomize edit set image bmtickets/web-ui=$REPO:$TAG
```

### 5.2 Apply

```bash
kubectl apply -k book_my_ticket_web_ui/deploy/k8s
kubectl -n bmtickets rollout status deploy/web-ui
kubectl -n bmtickets get ingress bmtickets-web     # ALB DNS name, shared with the API ingress
```

Point Route 53 `www.bmtickets.example` at the ALB with an alias A/AAAA record. ExternalDNS can do this automatically if you run it.

### 5.3 Gateway CORS

With the same-origin setup above, **no CORS change is needed**.

If you serve the UI from a different origin than the API (for example `BMT_API_BASE_URL=https://api.bmtickets.example`), you must do two things:

1. Allow that origin on the gateway: set `CORS_ALLOWED_ORIGINS=https://www.bmtickets.example` (see `k8s/base/services/api-gateway.yaml`).
2. Add the API origin to `CSP_CONNECT_SRC`.

### 5.4 Verify

```bash
curl -s https://www.bmtickets.example/healthz
curl -s https://www.bmtickets.example/config.js
curl -s "https://www.bmtickets.example/api/v1/catalog/events?city=Mumbai" | jq '.totalElements'
curl -sI https://www.bmtickets.example/ | grep -iE 'content-security|strict-transport'
```

Then in a browser: sign in, open a show, hold seats, pay with a Stripe test card in staging, and confirm the e-ticket appears.

---

## 6. Payments: Stripe

| Environment | payment-service | Web UI |
|---|---|---|
| Local | `PAYMENT_PROVIDER=fake` | `BMT_PAYMENT_MODE=fake`. The **Pay** button calls the dev webhook simulator |
| Staging | Stripe test keys | `BMT_PAYMENT_MODE=stripe`, `BMT_STRIPE_PUBLISHABLE_KEY=pk_test_…` |
| Production | Stripe live keys (Secrets Manager) | `BMT_PAYMENT_MODE=stripe`, `BMT_STRIPE_PUBLISHABLE_KEY=pk_live_…` |

How a Stripe payment completes:

1. The UI mounts Stripe's Payment Element using the booking's `paymentClientSecret`.
2. `stripe.confirmPayment` sends the card details directly to Stripe. They never reach BookMyTickets servers (PCI SAQ-A scope).
3. Stripe's webhook goes to `payment-service`, which advances the booking saga to `CONFIRMED`.
4. The UI polls `GET /api/v1/bookings/{id}` until it sees `CONFIRMED`.

The CSP already allows `js.stripe.com` (script and frame) and `api.stripe.com` (connect). The publishable key is designed to be public, so a ConfigMap is fine for it. The **secret** key stays in payment-service only.

---

## 7. Alternative: S3 + CloudFront

If you prefer not to run web pods, host the static build on S3 + CloudFront:

```bash
npm ci && npm test && npm run build      # dist/ (dev config.js removed below)
rm dist/config.js
cat > dist/config.js <<'EOF'
window.__BMT_CONFIG__ = { apiBaseUrl: "", authUrl: "https://cognito-idp.ap-south-1.amazonaws.com/ap-south-1_XXXX",
  clientId: "<client-id>", paymentMode: "stripe", stripePublishableKey: "pk_live_…", defaultCity: "Mumbai",
  cities: ["Mumbai","Delhi","Bengaluru"] };
EOF
aws s3 sync dist/assets s3://<bucket>/assets --cache-control "public,max-age=31536000,immutable"
aws s3 sync dist s3://<bucket> --exclude "assets/*" --cache-control "no-cache"
aws cloudfront create-invalidation --distribution-id <id> --paths "/index.html" "/config.js"
```

CloudFront settings:

- **Origins.** The S3 bucket (private, accessed through Origin Access Control) for the default `*` behaviour, and the platform ALB for the `/api/*` behaviour. For `/api/*`, disable caching and forward all headers, cookies and query strings.
- **SPA routing.** Add a CloudFront Function that rewrites extension-less paths to `/index.html`, or map 403 and 404 errors to `/index.html` with a 200 status.
- **Security headers.** Add a response headers policy with the CSP from `nginx/security-headers.inc.template`, plus HSTS.
- **WAF.** Attach a CloudFront-scope web ACL.

---

## 8. CI/CD pipeline

A sample GitHub Actions job. Adapt it to CodePipeline or GitLab the same way.

```yaml
name: web-ui
on:
  push:
    branches: [main]
    paths: ["book_my_ticket_web_ui/**"]
permissions: { id-token: write, contents: read }
jobs:
  build-deploy:
    runs-on: ubuntu-latest
    defaults: { run: { working-directory: book_my_ticket_web_ui } }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: 22, cache: npm, cache-dependency-path: book_my_ticket_web_ui/package-lock.json }
      - run: npm ci
      - run: npm run lint && npm run typecheck && npm run test:coverage
      - uses: aws-actions/configure-aws-credentials@v4
        with: { role-to-assume: arn:aws:iam::<ACCOUNT_ID>:role/bmtickets-ci, aws-region: ap-south-1 }
      - uses: aws-actions/amazon-ecr-login@v2
        id: ecr
      - name: Build & push (tests already ran above)
        run: |
          IMAGE=${{ steps.ecr.outputs.registry }}/bmtickets/web-ui:${GITHUB_SHA::7}
          docker build --build-arg SKIP_TESTS=true -t $IMAGE .
          docker push $IMAGE
          echo "IMAGE=$IMAGE" >> $GITHUB_ENV
      - name: Deploy
        run: |
          aws eks update-kubeconfig --name bmtickets-prod
          cd deploy/k8s && kustomize edit set image bmtickets/web-ui=$IMAGE
          kubectl apply -k . && kubectl -n bmtickets rollout status deploy/web-ui --timeout=180s
```

For GitOps (Argo CD or Flux), commit the `kustomize edit set image` change to the environment repository instead of running `kubectl apply`.

---

## 9. Rollout, rollback and operations

- **Zero-downtime rollout.** The rollout uses `maxUnavailable: 0`, readiness on `/healthz` and a 10 s `preStop` sleep while the ALB deregisters each target.
  - Old hashed assets disappear when old pods terminate. A user who loaded `index.html` from an old pod may fail to lazy-load a route chunk until they reload.
  - To avoid this entirely, serve `/assets/*` from S3/CloudFront (section 7) and keep the previous release's assets there.
- **Rollback:** run `kubectl -n bmtickets rollout undo deploy/web-ui`, or re-apply the previous image tag.
- **Config change without a rebuild:** edit the ConfigMap, then run `kubectl -n bmtickets rollout restart deploy/web-ui`.
- **Scaling.** Static serving is cheap (about 50m CPU and 64Mi memory per pod). The HPA covers spikes such as a big on-sale. The real bottleneck is the booking path, which the gateway rate-limits.
- **Observability.**
  - nginx access logs go to stdout and are collected by your log agent (Fluent Bit to CloudWatch).
  - Browser-side errors are logged to the console by the error boundary. Wire `componentDidCatch` in `src/App.tsx` to your RUM tool (CloudWatch RUM, Sentry) if you need them centrally.
  - API traces start at the gateway (OpenTelemetry → X-Ray/Jaeger).

---

## 10. Go-live checklist

- [ ] The image tag is an immutable git SHA, and ECR scan-on-push shows no critical CVEs
- [ ] The Cognito app client is **public** (no secret), callback URL `https://<host>/auth/callback`, and the `ADMIN` group exists
- [ ] `BMT_AUTH_URL` is the Cognito **issuer** URL, and `BMT_CLIENT_ID` is set
- [ ] `BMT_PAYMENT_MODE=stripe` with the right `pk_` key, and payment-service has the matching secret key and webhook secret
- [ ] `CSP_CONNECT_SRC` includes the Cognito issuer, the hosted-UI domain and `https://api.stripe.com`. There are no CSP violations in the browser console
- [ ] The ingress joins group `bmtickets`, the WAF ACL is attached, the ACM certificate covers the host, and HTTP redirects to HTTPS
- [ ] The NetworkPolicy CIDR matches the VPC
- [ ] `/api/*` reaches the gateway on the web host. Check `curl https://<host>/api/v1/catalog/events?city=Mumbai`
- [ ] An end-to-end booking in staging works with a Stripe test card, and the e-ticket QR code renders
- [ ] Rollback has been tested (`kubectl rollout undo`)
