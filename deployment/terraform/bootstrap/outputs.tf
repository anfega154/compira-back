output "terraform_state_bucket" {
  value = aws_s3_bucket.terraform_state.bucket
}

output "terraform_lock_table" {
  value = aws_dynamodb_table.terraform_lock.name
}

output "github_actions_role_arn" {
  value = aws_iam_role.github_actions_qa.arn
}
