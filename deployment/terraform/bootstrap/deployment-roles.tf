data "aws_caller_identity" "current" {}

locals {
  prefix     = "${var.tags.project}-${var.tags.environment}"
  account    = data.aws_caller_identity.current.account_id
  ecs_prefix = "arn:aws:ecs:${var.aws_region}:${local.account}"
}

resource "aws_iam_role_policy" "backend_deploy" {
  role = aws_iam_role.github_actions_qa.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
      { Effect = "Allow", Action = ["ecr:BatchCheckLayerAvailability", "ecr:InitiateLayerUpload", "ecr:UploadLayerPart", "ecr:CompleteLayerUpload", "ecr:PutImage", "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer", "ecr:DescribeImages"], Resource = "arn:aws:ecr:${var.aws_region}:${local.account}:repository/${local.prefix}-api" },
      { Effect = "Allow", Action = ["ecs:DescribeServices", "ecs:UpdateService"], Resource = "${local.ecs_prefix}:service/${local.prefix}-cluster/${local.prefix}-service" },
      { Effect = "Allow", Action = ["ecs:DescribeTaskDefinition"], Resource = "${local.ecs_prefix}:task-definition/${local.prefix}-task:*" },
      # RegisterTaskDefinition does not support resource-level permissions.
      { Effect = "Allow", Action = ["ecs:RegisterTaskDefinition"], Resource = "*" },
      { Effect = "Allow", Action = ["iam:PassRole"], Resource = ["arn:aws:iam::${local.account}:role/${local.prefix}-ecs-execution-role", "arn:aws:iam::${local.account}:role/${local.prefix}-ecs-task-role"], Condition = { StringEquals = { "iam:PassedToService" = "ecs-tasks.amazonaws.com" } } }
    ]
  })
}

data "aws_iam_policy_document" "frontend_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github_actions.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "ForAnyValue:StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values = flatten([
        for repository in var.github_repositories : [
          "repo:${repository}:environment:qa",
          "repo:${split("/", repository)[0]}@*/${split("/", repository)[1]}@*:environment:qa"
        ] if endswith(repository, "/compira-front")
      ])
    }
  }
}

resource "aws_iam_role" "frontend_deploy" {
  name               = "${local.prefix}-frontend-deploy"
  assume_role_policy = data.aws_iam_policy_document.frontend_assume.json
  tags               = var.tags
}

resource "aws_iam_role_policy" "frontend_deploy" {
  role = aws_iam_role.frontend_deploy.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = ["s3:ListBucket"], Resource = "arn:aws:s3:::${local.prefix}-frontend-${local.account}" },
      { Effect = "Allow", Action = ["s3:GetObject", "s3:PutObject"], Resource = "arn:aws:s3:::${local.prefix}-frontend-${local.account}/*" },
      { Effect = "Allow", Action = ["cloudfront:CreateInvalidation", "cloudfront:GetInvalidation", "cloudfront:GetDistribution"], Resource = "arn:aws:cloudfront::${local.account}:distribution/${var.frontend_distribution_id}" }
    ]
  })
}
