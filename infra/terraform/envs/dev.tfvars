environment = "dev"
aws_region  = "ap-southeast-1"
vpc_cidr    = "10.10.0.0/16"

# Smaller / cheaper for dev.
node_instance_types = ["t3.large"]
node_desired_size   = 2
node_min_size       = 1
node_max_size       = 3

db_instance_class = "db.t3.medium"
db_multi_az       = false

redis_node_type               = "cache.t3.micro"
redis_replicas_per_node_group = 0 # single node for dev

# Create the account-global GitHub OIDC provider here (dev), then reference it
# from prod (create_github_oidc_provider = false).
create_github_oidc_provider = true
