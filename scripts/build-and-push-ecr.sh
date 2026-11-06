#!/usr/bin/env bash
# Build every service image (multi-arch: amd64 + arm64/Graviton) and push to Amazon ECR.
#
# Usage:  AWS_REGION=ap-south-1 AWS_ACCOUNT_ID=123456789012 ./scripts/build-and-push-ecr.sh [tag] [service ...]
#   tag      defaults to the short git SHA (immutable tags => reproducible, auditable deployments)
#   service  defaults to all deployable services
set -euo pipefail

: "${AWS_REGION:?set AWS_REGION}"
: "${AWS_ACCOUNT_ID:?set AWS_ACCOUNT_ID}"
TAG="${1:-$(git rev-parse --short HEAD 2>/dev/null || date +%Y%m%d%H%M%S)}"
shift || true
SERVICES=("$@")
if [ ${#SERVICES[@]} -eq 0 ]; then
  SERVICES=(api-gateway catalog-service seat-inventory-service booking-service payment-service notification-service)
fi
PLATFORMS="${PLATFORMS:-linux/amd64,linux/arm64}"
REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
PREFIX="${ECR_PREFIX:-bmtickets}"

echo ">> Logging in to ${REGISTRY}"
aws ecr get-login-password --region "${AWS_REGION}" | docker login --username AWS --password-stdin "${REGISTRY}"

docker buildx inspect bmtickets-builder >/dev/null 2>&1 || docker buildx create --name bmtickets-builder --use
docker buildx use bmtickets-builder

for svc in "${SERVICES[@]}"; do
  repo="${PREFIX}/${svc}"
  if ! aws ecr describe-repositories --repository-names "${repo}" --region "${AWS_REGION}" >/dev/null 2>&1; then
    echo ">> Creating ECR repository ${repo} (immutable tags, scan on push, KMS encryption)"
    aws ecr create-repository --repository-name "${repo}" --region "${AWS_REGION}" \
      --image-tag-mutability IMMUTABLE \
      --image-scanning-configuration scanOnPush=true \
      --encryption-configuration encryptionType=KMS >/dev/null
    aws ecr put-lifecycle-policy --repository-name "${repo}" --region "${AWS_REGION}" --lifecycle-policy-text \
      '{"rules":[{"rulePriority":1,"description":"keep last 30 images","selection":{"tagStatus":"any","countType":"imageCountMoreThan","countNumber":30},"action":{"type":"expire"}}]}' >/dev/null
  fi

  echo ">> Building and pushing ${repo}:${TAG} for ${PLATFORMS}"
  docker buildx build \
    --platform "${PLATFORMS}" \
    --file docker/Dockerfile \
    --build-arg SERVICE="${svc}" \
    --tag "${REGISTRY}/${repo}:${TAG}" \
    --provenance=true --sbom=true \
    --push .
done

echo ">> Done. Deploy with:"
echo "   cd k8s/overlays/prod && kustomize edit set image \\"
for svc in "${SERVICES[@]}"; do echo "     bmtickets/${svc}=${REGISTRY}/${PREFIX}/${svc}:${TAG} \\"; done
echo "   && kubectl apply -k ."
