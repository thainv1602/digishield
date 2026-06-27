environment = "dev"
aws_region  = "ap-southeast-1"
vpc_cidr    = "10.10.0.0/16"

# Smaller / cheaper for dev: 1 node steady-state, burst to 2 under load.
node_instance_types = ["t3.large"]
node_desired_size   = 1
node_min_size       = 1
node_max_size       = 2

db_instance_class = "db.t3.medium"
db_multi_az       = false

redis_node_type               = "cache.t3.micro"
redis_replicas_per_node_group = 0 # single node for dev

# Create the account-global GitHub OIDC provider + shared ECR repo here (dev),
# then reference them from prod (create_* = false).
create_github_oidc_provider = true
create_ecr                  = true
