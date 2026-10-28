variable "aws_region" {
  type        = string
  description = "AWS region for Terraform bootstrap resources."
  default     = "us-east-1"
}

variable "terraform_state_bucket" {
  type        = string
  description = "Globally unique S3 bucket name used for Terraform remote state."
}

variable "terraform_lock_table" {
  type        = string
  description = "DynamoDB table name used for Terraform state locking."
}

variable "tags" {
  type        = map(string)
  description = "Tags applied to bootstrap resources."
  default = {
    project     = "compira"
    managed-by  = "terraform"
    environment = "qa"
  }
}

variable "github_repositories" {
  type        = set(string)
  description = "Repositories allowed to assume deployment roles through the protected GitHub qa environment."
}

variable "frontend_distribution_id" {
  type        = string
  description = "CloudFront distribution permitted for frontend CD. Set the exact ID after first apply."
  default     = "*"
}
