data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

locals {
  name_prefix = "${var.project}-${var.environment}"
  tags = merge(
    {
      project     = var.project
      environment = var.environment
      managed-by  = "terraform"
    },
    var.tags
  )
}

resource "aws_iam_role" "cognito_sms" {
  name = "${local.name_prefix}-cognito-sms-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "cognito-idp.amazonaws.com"
        }
        Action = "sts:AssumeRole"
        Condition = {
          StringEquals = {
            "sts:ExternalId" = var.sms_external_id
          }
        }
      }
    ]
  })

  tags = local.tags
}

resource "aws_iam_role_policy" "cognito_sms_publish" {
  name = "${local.name_prefix}-cognito-sms-publish"
  role = aws_iam_role.cognito_sms.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "sns:Publish"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_cognito_user_pool" "this" {
  name                     = "${local.name_prefix}-user-pool"
  user_pool_tier           = var.user_pool_tier
  username_attributes      = ["email"]
  auto_verified_attributes = ["email", "phone_number"]
  mfa_configuration        = "ON"

  password_policy {
    minimum_length                   = var.password_minimum_length
    require_lowercase                = true
    require_numbers                  = true
    require_symbols                  = true
    require_uppercase                = true
    temporary_password_validity_days = 7
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_phone_number"
      priority = 1
    }

    recovery_mechanism {
      name     = "verified_email"
      priority = 2
    }
  }

  admin_create_user_config {
    allow_admin_create_user_only = false
  }

  verification_message_template {
    default_email_option = "CONFIRM_WITH_CODE"
    email_subject        = var.verification_email_subject
    email_message        = var.verification_email_message
    sms_message          = var.verification_sms_message
  }

  email_configuration {
    email_sending_account  = "DEVELOPER"
    source_arn             = var.ses_source_arn
    from_email_address     = var.ses_from_email_address
    reply_to_email_address = var.ses_reply_to_email_address
  }

  email_mfa_configuration {
    message = var.email_mfa_message
    subject = var.email_mfa_subject
  }

  sms_authentication_message = var.sms_mfa_message

  sms_configuration {
    external_id    = var.sms_external_id
    sns_caller_arn = aws_iam_role.cognito_sms.arn
    sns_region     = data.aws_region.current.name
  }

  tags = local.tags
}

resource "aws_cognito_user_pool_client" "this" {
  name                                          = "${local.name_prefix}-app-client"
  user_pool_id                                  = aws_cognito_user_pool.this.id
  generate_secret                               = false
  prevent_user_existence_errors                 = "ENABLED"
  supported_identity_providers                  = ["COGNITO"]
  explicit_auth_flows                           = ["ALLOW_USER_PASSWORD_AUTH", "ALLOW_REFRESH_TOKEN_AUTH"]
  access_token_validity                         = var.access_token_validity_minutes
  id_token_validity                             = var.id_token_validity_minutes
  refresh_token_validity                        = var.refresh_token_validity_days
  enable_token_revocation                       = true
  enable_propagate_additional_user_context_data = false

  token_validity_units {
    access_token  = "minutes"
    id_token      = "minutes"
    refresh_token = "days"
  }
}
