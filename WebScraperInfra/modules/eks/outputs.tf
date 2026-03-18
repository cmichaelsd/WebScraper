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

output "api_database_url" {
  value     = local.asyncpg_url
  sensitive = true
}

output "jdbc_url" {
  value     = local.jdbc_url
  sensitive = true
}
