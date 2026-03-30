output "cluster_name" {
  value = aws_eks_cluster.this.name
}

output "cluster_endpoint" {
  value = aws_eks_cluster.this.endpoint
}

output "cluster_ca_certificate" {
  value = aws_eks_cluster.this.certificate_authority[0].data
}

output "pod_role_arn" {
  value = aws_iam_role.pods.arn
}

output "lbc_role_arn" {
  value = aws_iam_role.lbc.arn
}

output "connection_secret_arn" {
  value = aws_secretsmanager_secret.connection.arn
}
