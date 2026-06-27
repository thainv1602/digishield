# ECR repository for the app image (the runtime registry EKS pulls from).
# Account/region-global -> create once (e.g. in dev) and reference from the other
# env with create_ecr = false. The image is built once and promoted by tag.

variable "create_ecr" {
  description = "Create the shared ECR repository (true in exactly one env)."
  type        = bool
  default     = true
}

resource "aws_ecr_repository" "app" {
  count                = var.create_ecr ? 1 : 0
  name                 = "digishield/app"
  image_tag_mutability = "MUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  count      = var.create_ecr ? 1 : 0
  repository = aws_ecr_repository.app[0].name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Expire untagged images after 14 days"
      selection = {
        tagStatus   = "untagged"
        countType   = "sinceImagePushed"
        countUnit   = "days"
        countNumber = 14
      }
      action = { type = "expire" }
    }]
  })
}

data "aws_ecr_repository" "app" {
  count = var.create_ecr ? 0 : 1
  name  = "digishield/app"
}

locals {
  ecr_repository_url = var.create_ecr ? aws_ecr_repository.app[0].repository_url : data.aws_ecr_repository.app[0].repository_url
  ecr_repository_arn = var.create_ecr ? aws_ecr_repository.app[0].arn : data.aws_ecr_repository.app[0].arn
}
