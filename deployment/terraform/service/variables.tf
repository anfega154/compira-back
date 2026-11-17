variable "aws_region" { type = string }
variable "project" { type = string }
variable "environment" { type = string }
variable "app_port" {
  type = number
  validation {
    condition     = var.app_port == 8080
    error_message = "The current Spring configuration listens on port 8080."
  }
}
variable "desired_count" { type = number }
variable "container_cpu" { type = number }
variable "container_memory" { type = number }
variable "health_check_path" { type = string }
variable "api_gateway_stage_name" { type = string }
variable "image_tag" { type = string }
variable "vpc_cidr" { type = string }
variable "public_subnet_cidrs" { type = list(string) }
variable "private_subnet_cidrs" { type = list(string) }
variable "log_retention_days" { type = number }
variable "db_name" { type = string }
variable "db_master_username" { type = string }
variable "aurora_min_acu" { type = number }
variable "aurora_max_acu" { type = number }
variable "cognito_region" { type = string }
variable "cognito_user_pool_id" { type = string }
variable "cognito_client_id" { type = string }
variable "cors_allowed_origins" {
  type = string
  validation {
    condition     = length(trimspace(var.cors_allowed_origins)) > 0 && !strcontains(var.cors_allowed_origins, "*")
    error_message = "Set explicit allowed origins; do not use wildcard origins."
  }
}
variable "tags" {
  type    = map(string)
  default = {}
}

variable "db_deletion_protection" {
  type    = bool
  default = true
}
variable "api_throttling_burst_limit" {
  type    = number
  default = 50
}
variable "api_throttling_rate_limit" {
  type    = number
  default = 25
}
