variable "project_name" {
  type = string
}

variable "api_image" {
  type = string
}

variable "worker_image" {
  type = string
}

variable "db_endpoint" {
  type = string
}

variable "db_username" {
  type = string
}

variable "db_port" {
  type = number
}

variable "db_password" {
  type = string
}

variable "db_name" {
  type = string
}

variable "region" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "api_sg_id" {
  type = string
}

variable "worker_sg_id" {
  type = string
}

variable "target_group_arn" {
  type = string
}