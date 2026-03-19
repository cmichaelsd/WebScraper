module "vpc" {
  source       = "./modules/vpc"
  project_name = var.project_name
  cluster_name = var.project_name
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

module "alb" {
  source       = "./modules/alb"
  project_name = var.project_name

  vpc_id            = module.vpc.vpc_id
  public_subnet_ids = module.vpc.public_subnets
  alb_sg_id         = module.security_groups.alb_sg_id
}

module "eks" {
  source       = "./modules/eks"
  project_name = var.project_name

  private_subnet_ids = module.vpc.private_subnets
  public_subnet_ids  = module.vpc.public_subnets

  db_endpoint   = module.rds.db_endpoint
  db_port       = module.rds.db_port
  db_username   = module.rds.db_username
  db_password   = module.rds.db_password
  db_name       = var.db_name
  db_secret_arn = module.rds.db_secret_arn

  rds_sg_id = module.security_groups.rds_sg_id
  alb_sg_id = module.security_groups.alb_sg_id
}

module "k8s" {
  source = "./modules/k8s"

  aws_region       = var.aws_region
  cluster_name     = module.eks.cluster_name
  vpc_id           = module.vpc.vpc_id
  lbc_role_arn     = module.eks.lbc_role_arn
  pod_role_arn     = module.eks.pod_role_arn
  target_group_arn = module.alb.target_group_arn
  api_database_url = module.eks.api_database_url
  jdbc_url         = module.eks.jdbc_url
  db_username      = module.rds.db_username
  db_password      = module.rds.db_password

  depends_on = [module.eks]
}