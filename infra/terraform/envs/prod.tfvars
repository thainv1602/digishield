environment = "prod"
aws_region  = "ap-southeast-1"
vpc_cidr    = "10.20.0.0/16"

node_instance_types = ["t3.large"]
node_desired_size   = 3
node_min_size       = 2
node_max_size       = 6

db_instance_class    = "db.r6g.large"
db_allocated_storage = 100
db_multi_az          = true

redis_node_type               = "cache.t3.small"
redis_replicas_per_node_group = 1 # primary + replica for HA

# OIDC provider already created by the dev apply; reference it here.
create_github_oidc_provider = false
