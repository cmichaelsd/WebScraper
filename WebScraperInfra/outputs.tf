output "cluster_name" {
  value = module.eks.cluster_name
}

output "pod_role_arn" {
  value = module.eks.pod_role_arn
}

output "api_database_url" {
  value     = module.eks.api_database_url
  sensitive = true
}

output "jdbc_url" {
  value     = module.eks.jdbc_url
  sensitive = true
}

output "db_username" {
  value = module.rds.db_username
}

output "db_password" {
  value     = module.rds.db_password
  sensitive = true
}
