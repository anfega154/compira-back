data "tls_certificate" "github_actions" {
  url = "https://token.actions.githubusercontent.com"
}

resource "aws_iam_openid_connect_provider" "github_actions" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.github_actions.certificates[0].sha1_fingerprint]
}

data "aws_iam_policy_document" "github_actions_assume" {
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
          "repo:${repository}:ref:refs/heads/qa",
          "repo:${repository}:environment:qa"
        ]
      ])
    }
  }
}

resource "aws_iam_role" "github_actions_qa" {
  name               = "${var.tags.project}-${var.tags.environment}-github-actions"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume.json
  tags               = var.tags
}

# Initial bootstrap policy. Replace with a least-privilege policy after the QA stack is stable.
resource "aws_iam_role_policy_attachment" "github_actions_qa" {
  role       = aws_iam_role.github_actions_qa.name
  policy_arn = "arn:aws:iam::aws:policy/AdministratorAccess"
}
