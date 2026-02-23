output "api_sg_id" {
  value = aws_security_group.api.id
}

output "worker_sg_id" {
  value = aws_security_group.worker.id
}

output "rds_sg_id" {
  value = aws_security_group.rds.id
}

output "alb_sg_id" {
  value = aws_security_group.alb.id
}