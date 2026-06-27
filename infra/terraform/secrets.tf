# Generated credentials stored in AWS Secrets Manager. The Helm chart's
# External Secrets pull these into Kubernetes Secrets:
#   digishield/<env>/db    -> { "password": ... }      (deployment DB_PASSWORD)
#   digishield/<env>/redis -> { "auth-token": ... }    (deployment REDIS_PASSWORD)

resource "random_password" "db" {
  length           = 24
  special          = true
  override_special = "!#$%*-_=+"
}

# ElastiCache auth tokens disallow most specials (16-128 chars).
resource "random_password" "redis" {
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "db" {
  name = "${local.secret_path}/db"
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    username = var.db_username
    password = random_password.db.result
  })
}

resource "aws_secretsmanager_secret" "redis" {
  name = "${local.secret_path}/redis"
}

resource "aws_secretsmanager_secret_version" "redis" {
  secret_id = aws_secretsmanager_secret.redis.id
  secret_string = jsonencode({
    "auth-token" = random_password.redis.result
  })
}
