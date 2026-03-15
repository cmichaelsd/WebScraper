# WebScraper Infra


## Overview
This project essentially creates scalable API and worker instances. API is publicly reachable, Worker is not. Both of these instances can communicate with a PSQL acting as a queue DB.


## Terraform steps
1) First time setup `terraform init`
2) After any changes are made always run `terraform fmt -recursive`
3) Validate changes `terraform validate`
4) Produce a named plan `terraform plan -out=tfplan`
5) Apply named plan to AWS `terraform apply tfplan`
6) When done run `terraform destory`

## How to access deployed application
1) Sign-in to AWS
2) Navigate to EC2
3) Find Load Balancers and select webscraper-alb
4) Navigate to the DNS name at /docs like this: webscraper-alb-<hash>.us-west-1.elb.amazonaws.com
