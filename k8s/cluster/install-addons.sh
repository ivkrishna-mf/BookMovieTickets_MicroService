#!/usr/bin/env bash
# Cluster add-ons needed by the manifests in k8s/base (run once per cluster).
set -euo pipefail
CLUSTER="${CLUSTER:-bmtickets-prod}"
REGION="${AWS_REGION:-ap-south-1}"
VPC_ID="$(aws eks describe-cluster --name "$CLUSTER" --region "$REGION" --query 'cluster.resourcesVpcConfig.vpcId' --output text)"

helm repo add eks https://aws.github.io/eks-charts
helm repo add external-secrets https://charts.external-secrets.io
helm repo add kedacore https://kedacore.github.io/charts
helm repo add autoscaler https://kubernetes.github.io/autoscaler
helm repo update

# ALB Ingress (Ingress -> Application Load Balancer, WAF association)
helm upgrade --install aws-load-balancer-controller eks/aws-load-balancer-controller -n kube-system \
  --set clusterName="$CLUSTER" --set region="$REGION" --set vpcId="$VPC_ID" \
  --set serviceAccount.create=false --set serviceAccount.name=aws-load-balancer-controller

# External Secrets Operator (AWS Secrets Manager -> Kubernetes Secrets)
helm upgrade --install external-secrets external-secrets/external-secrets -n external-secrets --create-namespace

# KEDA (Kafka-lag based autoscaling for notification-service)
helm upgrade --install keda kedacore/keda -n keda --create-namespace

# Cluster Autoscaler (or install Karpenter instead)
helm upgrade --install cluster-autoscaler autoscaler/cluster-autoscaler -n kube-system \
  --set autoDiscovery.clusterName="$CLUSTER" --set awsRegion="$REGION"

# Observability pipeline (ADOT collector -> X-Ray + Amazon Managed Prometheus)
kubectl apply -f "$(dirname "$0")/../observability/otel-collector.yaml"

echo "Add-ons installed. Next: kubectl apply -k k8s/overlays/prod"
