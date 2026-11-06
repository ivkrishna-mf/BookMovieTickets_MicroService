# Kubernetes / Amazon EKS resources

```
k8s/
├── cluster/
│   ├── eksctl-cluster.yaml     # EKS 1.33, 3 AZs, Graviton node groups, IRSA roles, add-ons (VPC CNI w/ NetworkPolicy)
│   └── install-addons.sh       # ALB controller, External Secrets, KEDA, Cluster Autoscaler, ADOT collector
├── aws/
│   └── waf-web-acl.json        # AWS WAF: per-IP rate-based rules + managed OWASP / bot-control rule groups
├── observability/
│   └── otel-collector.yaml     # ADOT collector: OTLP traces -> X-Ray, Prometheus scrape -> Amazon Managed Prometheus
├── base/                       # environment-agnostic manifests (kustomize)
│   ├── namespace.yaml          # Pod Security "restricted"
│   ├── platform-config.yaml    # shared 12-factor config (ConfigMap)
│   ├── external-secrets.yaml   # AWS Secrets Manager -> K8s Secrets (DB creds, Stripe keys, signing key)
│   ├── serviceaccounts.yaml    # one identity per service (IRSA)
│   ├── rbac-discovery.yaml     # Spring Cloud Kubernetes discovery (K8s API replaces Eureka)
│   ├── network-policies.yaml   # default-deny + explicit service-to-service allow list
│   ├── ingress.yaml            # ALB (TLS 1.3, WAF association, health checks)
│   ├── keda-notification.yaml  # scale notification-service on Kafka consumer lag
│   └── services/*.yaml         # Deployment + Service (+ headless gRPC Service) + HPA + PDB per microservice
└── overlays/
    ├── dev/                    # 1 replica, 100% trace sampling, Stripe test mode
    └── prod/                   # ECR images with immutable tags, real endpoints
```

## Prerequisites (managed AWS services, created once - e.g. with Terraform/CDK)

| Resource | Purpose | Notes |
|---|---|---|
| Amazon ECR repos `bmtickets/<service>` | container images | created automatically by `scripts/build-and-push-ecr.sh` |
| Aurora PostgreSQL (Multi-AZ) + **RDS Proxy** per service DB | database-per-service | RDS Proxy pools connections from hundreds of pods |
| ElastiCache for Redis (cluster mode, Multi-AZ, TLS) | cache, seat holds, idempotency, rate-limit buckets, ShedLock | hash tags `{showId}` keep multi-key Lua scripts slot-local |
| Amazon MSK (3 brokers, 3 AZs, TLS) | Kafka event backbone | RF=3, min.insync.replicas=2 |
| Amazon Cognito user pool + app clients | AuthN (users: PKCE; services: client-credentials, scope `bmtickets/internal`) | Pre-token-generation Lambda adds `email` to access tokens |
| AWS Secrets Manager `bmtickets/<service>` | DB_HOST/DB_USERNAME/DB_PASSWORD, STRIPE_*, SERVICE_CLIENT_*, TICKET_SIGNING_KEY | rotated; synced by External Secrets |
| Amazon SES (production access, verified domain) | ticket e-mails | configuration set -> SNS for bounces |
| AWS WAF web ACL (`aws/waf-web-acl.json`) | edge rate limiting, bot control | ARN goes into `ingress.yaml` |
| ACM certificate, Route 53 record | TLS, DNS | |
| Amazon Managed Prometheus + Managed Grafana, X-Ray | observability | |

## Deploy

```bash
# 1. cluster + add-ons
eksctl create cluster -f k8s/cluster/eksctl-cluster.yaml
./k8s/cluster/install-addons.sh

# 2. images -> ECR (multi-arch, immutable tag = git SHA)
AWS_REGION=ap-south-1 AWS_ACCOUNT_ID=123456789012 ./scripts/build-and-push-ecr.sh

# 3. set image tags + endpoints, then apply
cd k8s/overlays/prod
kustomize edit set image bmtickets/booking-service=123456789012.dkr.ecr.ap-south-1.amazonaws.com/bmtickets/booking-service:<sha>   # ...per service
kubectl apply -k .

# 4. verify
kubectl -n bmtickets get pods,hpa,scaledobject,ingress
kubectl -n bmtickets rollout status deploy/booking-service
```

Replace every `<PLACEHOLDER>` (account id, Cognito pool, MSK/ElastiCache endpoints, ACM/WAF ARNs) for your account.

## What runs where

* **Service discovery** - `lb://booking-service` (gateway) and `@FeignClient("payment-service")` are resolved by
  Spring Cloud Kubernetes LoadBalancer in `SERVICE` mode -> the Kubernetes Service DNS name. Eureka is not deployed on EKS.
* **gRPC** - booking-service dials `dns:///<svc>-grpc...:909x` (headless Service) with `round_robin`, so every pod
  receives traffic despite long-lived HTTP/2 connections.
* **Zero-downtime deploys** - `maxUnavailable: 0`, readiness probes, `preStop` sleep + Spring graceful shutdown,
  PDBs, topology spread across 3 AZs.
* **Autoscaling** - HPA on CPU (fast scale-up / slow scale-down) for request-driven services; KEDA on Kafka lag for
  notification-service; Cluster Autoscaler / Karpenter adds nodes.
