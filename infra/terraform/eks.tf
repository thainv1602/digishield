module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.24"

  cluster_name    = local.name
  cluster_version = var.eks_version

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  # Public endpoint so GitHub Actions can run `aws eks update-kubeconfig`.
  # Restrict cluster_endpoint_public_access_cidrs in production.
  cluster_endpoint_public_access = true

  enable_irsa = true # creates the cluster OIDC provider used by IRSA roles

  eks_managed_node_groups = {
    default = {
      instance_types = var.node_instance_types
      min_size       = var.node_min_size
      max_size       = var.node_max_size
      desired_size   = var.node_desired_size
    }
  }

  # Grant the GitHub Actions deploy role cluster-admin so `helm upgrade` works.
  authentication_mode = "API_AND_CONFIG_MAP"
  access_entries = {
    github_deploy = {
      principal_arn = aws_iam_role.github_deploy.arn
      policy_associations = {
        admin = {
          policy_arn = "arn:${local.partition}:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"
          access_scope = {
            type = "cluster"
          }
        }
      }
    }
  }
}
