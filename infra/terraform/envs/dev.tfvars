environment = "dev"
aws_region  = "ap-southeast-1"
vpc_cidr    = "10.10.0.0/16"

# Free-tier-eligible node (2 vCPU / 8GB). t3.large is blocked on the AWS Free
# plan; m7i-flex.large is free-tier-eligible in ap-southeast-1.
node_instance_types = ["m7i-flex.large"]
node_desired_size   = 1
node_min_size       = 1
node_max_size       = 2

# db.t3.micro is the free-tier-eligible RDS class (db.t3.medium is not).
db_instance_class = "db.t3.micro"
db_multi_az       = false
# 16.4 was removed by AWS; 16.9 is the oldest still-available 16.x in this region.
db_engine_version = "16.9"

redis_node_type               = "cache.t3.micro"
redis_replicas_per_node_group = 0 # single node for dev

# Create the account-global GitHub OIDC provider here (dev), then reference it
# from prod (create_github_oidc_provider = false).
create_github_oidc_provider = true
