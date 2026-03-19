variable "aws_region" {
  type = string
}

variable "cluster_name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "lbc_role_arn" {
  type = string
}

variable "pod_role_arn" {
  type = string
}

variable "target_group_arn" {
  type = string
}

variable "api_database_url" {
  type      = string
  sensitive = true
}

variable "jdbc_url" {
  type      = string
  sensitive = true
}

variable "db_username" {
  type = string
}

variable "db_password" {
  type      = string
  sensitive = true
}
