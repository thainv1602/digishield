# IRSA role for the External Secrets Operator service account, so it can read
# the digishield/<env>/* secrets from AWS Secrets Manager without static keys.
# Annotate the ESO ServiceAccount with this role ARN (see README).

data "aws_iam_policy_document" "eso_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [module.eks.oidc_provider_arn]
    }
    condition {
      test     = "StringEquals"
      variable = "${module.eks.oidc_provider}:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "${module.eks.oidc_provider}:sub"
      # namespace:serviceaccount of the External Secrets Operator
      values = ["system:serviceaccount:external-secrets:external-secrets"]
    }
  }
}

resource "aws_iam_role" "external_secrets" {
  name               = "${local.name}-external-secrets"
  assume_role_policy = data.aws_iam_policy_document.eso_assume.json
}

data "aws_iam_policy_document" "external_secrets" {
  statement {
    actions = [
      "secretsmanager:GetSecretValue",
      "secretsmanager:DescribeSecret",
    ]
    resources = [
      "arn:${local.partition}:secretsmanager:${var.aws_region}:${local.account_id}:secret:${local.secret_path}/*",
    ]
  }
}

resource "aws_iam_role_policy" "external_secrets" {
  name   = "secretsmanager-read"
  role   = aws_iam_role.external_secrets.id
  policy = data.aws_iam_policy_document.external_secrets.json
}
