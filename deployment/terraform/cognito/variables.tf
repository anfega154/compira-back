variable "aws_region" {
  type        = string
  description = "AWS region where Cognito resources are provisioned."
  default     = "us-east-1"
}

variable "project" {
  type        = string
  description = "Project name used as prefix for AWS resources."
  default     = "compira"
}

variable "environment" {
  type        = string
  description = "Environment name used in resource naming."
  default     = "dev"
}

variable "user_pool_tier" {
  type        = string
  description = "Cognito tier required to enable EMAIL_OTP MFA."
  default     = "ESSENTIALS"
}

variable "sms_external_id" {
  type        = string
  description = "External ID used by Cognito when assuming the SMS publish role."
}

variable "ses_source_arn" {
  type        = string
  description = "Verified SES identity ARN used by Cognito to send EMAIL_OTP MFA and verification emails."
}

variable "ses_from_email_address" {
  type        = string
  description = "From email address used by Cognito when SES is configured."
}

variable "ses_reply_to_email_address" {
  type        = string
  description = "Reply-to email address used by Cognito when SES is configured."
}

variable "verification_email_subject" {
  type        = string
  description = "Email subject used for sign-up confirmation."
  default     = "Confirm your Compira account"
}

variable "verification_sms_message" {
  type        = string
  description = "SMS message used for sign-up confirmation."
  default     = "Your Compira verification code is {####}"
}

variable "email_mfa_subject" {
  type        = string
  description = "Email subject used for EMAIL_OTP MFA."
  default     = "Your Compira sign-in code"
}

variable "invite_email_subject" {
  type        = string
  description = "Email subject for the admin-create-user invitation (temporary password)."
  default     = "Your temporary password"
}

variable "invite_sms_message" {
  type        = string
  description = "SMS message for the admin-create-user invitation (temporary password)."
  default     = "Bienvenido. Usuario: {username}. Contraseña temporal: {####}. Inicia sesión y cambia tu contraseña en el primer acceso."
}

variable "password_minimum_length" {
  type        = number
  description = "Minimum password length for Cognito users."
  default     = 10
}

variable "temporary_password_validity_days" {
  type        = number
  description = "Validity in days of admin-generated temporary passwords."
  default     = 2
}

variable "access_token_validity_minutes" {
  type        = number
  description = "Access token validity in minutes."
  default     = 60
}

variable "id_token_validity_minutes" {
  type        = number
  description = "ID token validity in minutes."
  default     = 60
}

variable "refresh_token_validity_days" {
  type        = number
  description = "Refresh token validity in days."
  default     = 30
}

variable "tags" {
  type        = map(string)
  description = "Additional tags applied to resources."
  default     = {}
}
