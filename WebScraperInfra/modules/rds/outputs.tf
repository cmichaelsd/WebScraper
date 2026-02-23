output "db_endpoint" {
  value = aws_db_instance.this.address
}

output "db_port" {
  value = aws_db_instance.this.port
}

output "db_username" {
  value = aws_db_instance.this.username
}

output "db_password" {
  value     = random_password.db.result
  sensitive = true
}
