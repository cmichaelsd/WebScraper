output "cluster_id" {
  value = aws_ecs_cluster.this.id
}

output "execution_role_arn" {
  value = aws_iam_role.ecs_task_execution_role.arn
}