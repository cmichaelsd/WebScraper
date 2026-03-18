# WebScraper


## Overview
This project is broken into three portions.
1) API - Python fastapi project which writes jobs to a PSQL as a queue DB.
2) Worker - Kotlin application which pulls from the PSQL queue and processes the submitted jobs.
3) Infra - Terraform project used to setup infra required to support this project.


## K8S
This project originally just used Docker but has since refactored to using Kubernetes. The K8S folder lives at root of this project and can be interacted with via the Makefile.


## Makefile
Instead of remembering a bunch of complicated commands just look in the Makefile and run the command you need.


## How to run locally
- For initial setup `make k8s-up`
- For code changes `make k8s-restart`
- For accessing in browser `make k8s-url` then navigate to /docs


## How to setup remote on AWS
- After following the instruction in WebScraperInfra run `make k8s-aws-deploy`
- For accessing in browser `make k8s-aws-url` then navigate to /docs


## Requirements
- awscli
- docker
- docker-compose
- minikube
- kubectl
