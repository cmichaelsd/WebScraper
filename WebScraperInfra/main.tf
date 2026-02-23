module "vpc" {
  source       = "./modules/vpc"
  project_name = var.project_name
}

module "security_groups" {
  source       = "./modules/security_groups"
  project_name = var.project_name
  vpc_id       = module.vpc.vpc_id
}

module "rds" {
  source       = "./modules/rds"
  project_name = var.project_name

  private_subnet_ids = module.vpc.private_subnets
  rds_sg_id          = module.security_groups.rds_sg_id

  db_name = var.db_name
}

module "ecs" {
  source       = "./modules/ecs"
  project_name = var.project_name

  api_image    = "657083456388.dkr.ecr.us-west-1.amazonaws.com/webscraper/api:latest"
  worker_image = "657083456388.dkr.ecr.us-west-1.amazonaws.com/webscraper/worker:latest"
  db_endpoint  = module.rds.db_endpoint
  db_port      = module.rds.db_port
  db_username  = module.rds.db_username
  db_password  = module.rds.db_password
  db_name      = var.db_name
  region       = var.aws_region

  private_subnet_ids = module.vpc.private_subnets
  api_sg_id          = module.security_groups.api_sg_id
  worker_sg_id       = module.security_groups.worker_sg_id
  target_group_arn   = module.alb.target_group_arn
}

module "alb" {
  source       = "./modules/alb"
  project_name = var.project_name

  vpc_id            = module.vpc.vpc_id
  alb_sg_id         = module.security_groups.alb_sg_id
  public_subnet_ids = module.vpc.public_subnets
}