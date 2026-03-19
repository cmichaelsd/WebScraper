# ── AWS Load Balancer Controller ──────────────────────────────────────────────
# Installs the controller into kube-system via Helm. The controller provides
# the TargetGroupBinding CRD that wires pod IPs into the Terraform-managed
# ALB target group.

resource "helm_release" "aws_load_balancer_controller" {
  name       = "aws-load-balancer-controller"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  namespace  = "kube-system"
  version    = "1.8.1"

  set {
    name  = "clusterName"
    value = var.cluster_name
  }

  set {
    name  = "serviceAccount.create"
    value = "true"
  }

  set {
    name  = "serviceAccount.name"
    value = "aws-load-balancer-controller"
  }

  set {
    name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = var.lbc_role_arn
  }

  set {
    name  = "region"
    value = var.aws_region
  }

  set {
    name  = "vpcId"
    value = var.vpc_id
  }
}

# ── Namespace ─────────────────────────────────────────────────────────────────

resource "kubectl_manifest" "namespace" {
  yaml_body = <<-YAML
    apiVersion: v1
    kind: Namespace
    metadata:
      name: webscraper
  YAML
}

# ── Service Account ───────────────────────────────────────────────────────────
# IRSA annotation lets pods assume the pod IAM role to access Secrets Manager.

resource "kubectl_manifest" "service_account" {
  yaml_body = <<-YAML
    apiVersion: v1
    kind: ServiceAccount
    metadata:
      name: webscraper-sa
      namespace: webscraper
      annotations:
        eks.amazonaws.com/role-arn: ${var.pod_role_arn}
  YAML

  depends_on = [kubectl_manifest.namespace]
}

# ── DB Credentials Secret ─────────────────────────────────────────────────────
# Kubernetes secret referenced by both the api and worker pods.

resource "kubectl_manifest" "db_credentials" {
  yaml_body = <<-YAML
    apiVersion: v1
    kind: Secret
    metadata:
      name: db-credentials
      namespace: webscraper
    type: Opaque
    data:
      DATABASE_URL: ${base64encode(var.api_database_url)}
      JDBC_URL: ${base64encode(var.jdbc_url)}
      DB_USER: ${base64encode(var.db_username)}
      DB_PASSWORD: ${base64encode(var.db_password)}
  YAML

  depends_on = [kubectl_manifest.namespace]
}

# ── TargetGroupBinding ────────────────────────────────────────────────────────
# Tells the controller to register pod IPs from the webscraper-api service
# into the Terraform-managed target group (and deregister on scale-down/rollout).
#
# Uses alekc/kubectl instead of hashicorp/kubernetes because kubectl_manifest
# defers CRD schema validation to apply time, so plan succeeds even before
# the LBC helm chart (and its CRDs) have been installed.

resource "kubectl_manifest" "target_group_binding" {
  yaml_body = <<-YAML
    apiVersion: elbv2.k8s.aws/v1beta1
    kind: TargetGroupBinding
    metadata:
      name: webscraper-api-tgb
      namespace: webscraper
    spec:
      serviceRef:
        name: webscraper-api
        port: 8000
      targetGroupARN: ${var.target_group_arn}
      targetType: ip
  YAML

  depends_on = [helm_release.aws_load_balancer_controller, kubectl_manifest.namespace]
}
