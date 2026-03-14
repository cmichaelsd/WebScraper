# WebScraper Infra

## Overview
This project essentially creates scalable API and worker instances. API is publicly reachable, Worker is not. Both of these instances can communicate with a PSQL acting as a queue DB.

## Terraform steps
1) First time setup `terraform init`
2) After any changes are made always run `terraform fmt -recursive`
3) Validate changes `terraform validate`
4) Produce a named plan `terraform plan -out=tfplan`
5) Apply named plan to AWS `terraform apply tfplan`
