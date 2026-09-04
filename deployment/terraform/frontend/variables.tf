variable "aws_region" {
  type    = string
  default = "us-east-1"
}
variable "project" {
  type    = string
  default = "compira"
}
variable "environment" {
  type    = string
  default = "qa"
}
variable "tags" {
  type    = map(string)
  default = {}
}
