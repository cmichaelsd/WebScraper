# ── Secrets Store CSI Driver ──────────────────────────────────────────────────
# Mounts Secrets Manager values into pods and syncs them to a K8s Secret so
# existing secretKeyRef env vars continue to work without modification.

resource "helm_release" "csi_secrets_store" {
  name       = "csi-secrets-store"
  repository = "https://kubernetes-sigs.github.io/secrets-store-csi-driver/charts"
  chart      = "secrets-store-csi-driver"
  namespace  = "kube-system"
  version    = "1.4.8"

  set {
    name  = "syncSecret.enabled"
    value = "true"
  }
}

resource "helm_release" "aws_secrets_provider" {
  name       = "secrets-provider-aws"
  repository = "https://aws.github.io/secrets-store-csi-driver-provider-aws"
  chart      = "secrets-store-csi-driver-provider-aws"
  namespace  = "kube-system"
  version    = "0.3.9"

  depends_on = [helm_release.csi_secrets_store]
}

# ── SecretProviderClass ───────────────────────────────────────────────────────
# Pulls all four connection values from the consolidated Secrets Manager secret
# and syncs them into a K8s Secret named db-credentials.

resource "kubectl_manifest" "secret_provider_class" {
  yaml_body = <<-YAML
    apiVersion: secrets-store.csi.x-k8s.io/v1
    kind: SecretProviderClass
    metadata:
      name: webscraper-secrets
      namespace: webscraper
    spec:
      provider: aws
      parameters:
        objects: |
          - objectName: "${var.connection_secret_arn}"
            objectType: secretsmanager
            jmesPath:
              - path: DATABASE_URL
                objectAlias: DATABASE_URL
              - path: JDBC_URL
                objectAlias: JDBC_URL
              - path: DB_USER
                objectAlias: DB_USER
              - path: DB_PASSWORD
                objectAlias: DB_PASSWORD
      secretObjects:
        - secretName: db-credentials
          type: Opaque
          data:
            - objectName: DATABASE_URL
              key: DATABASE_URL
            - objectName: JDBC_URL
              key: JDBC_URL
            - objectName: DB_USER
              key: DB_USER
            - objectName: DB_PASSWORD
              key: DB_PASSWORD
  YAML

  depends_on = [helm_release.aws_secrets_provider, kubectl_manifest.namespace]
}

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
