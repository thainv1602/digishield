resource "aws_elasticache_subnet_group" "this" {
  name       = "${local.name}-redis"
  subnet_ids = module.vpc.private_subnets
}

resource "aws_security_group" "redis" {
  name        = "${local.name}-redis"
  description = "Redis access from the EKS nodes only"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_security_group_rule" "redis_from_eks" {
  type                     = "ingress"
  from_port                = 6379
  to_port                  = 6379
  protocol                 = "tcp"
  security_group_id        = aws_security_group.redis.id
  source_security_group_id = module.eks.node_security_group_id
  description              = "Redis from EKS nodes"
}

resource "aws_elasticache_replication_group" "this" {
  replication_group_id = "${local.name}-redis"
  description          = "DigiShield ${var.environment} Redis"

  engine         = "redis"
  engine_version = var.redis_engine_version
  node_type      = var.redis_node_type
  port           = 6379

  num_node_groups            = var.redis_num_node_groups
  replicas_per_node_group    = var.redis_replicas_per_node_group
  automatic_failover_enabled = var.redis_replicas_per_node_group > 0
  multi_az_enabled           = var.environment == "prod" && var.redis_replicas_per_node_group > 0

  subnet_group_name  = aws_elasticache_subnet_group.this.name
  security_group_ids = [aws_security_group.redis.id]

  # In-transit encryption + auth token (matches Helm redis.tls=true + auth-token).
  transit_encryption_enabled = true
  auth_token                 = random_password.redis.result
  at_rest_encryption_enabled = true

  apply_immediately = var.environment != "prod"
}
