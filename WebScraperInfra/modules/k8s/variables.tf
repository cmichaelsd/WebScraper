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

variable "connection_secret_arn" {
  type = string
}
