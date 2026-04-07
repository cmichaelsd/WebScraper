# WebScraper


## Overview
This project is broken into three portions.
1) API - Python fastapi project which writes jobs to a PSQL as a queue DB.
2) Worker - Kotlin application which pulls from the PSQL queue and processes the submitted jobs.
3) Infra - Terraform project used to setup infra required to support this project.

![Diagram of service](./diagram.svg)


## Scaling Dimensions
This is a job/batch processing system, not a user-facing request/response system. DAU is essentially irrelevant -
the right scaling metrics are:

1) Concurrent jobs - how many jobs are running simultaneously
2) Pages per job - determined by max_depth and breadth of discovered URLs
3) Job queue depth - backlog of PENDING jobs in Postgres


### How Scaling Works
Workers are the unit of horizontal scale. Each worker pod:
- Claims one job at a time via FOR UPDATE SKIP LOCKED - Postgres acts as the queue coordinator
- Processes up to 5 pages concurrently within that job (Semaphore(5))
- Is completely stateless - spin up more replicas and they transparently pick up more jobs, no coordination needed

So: concurrent jobs = worker replica count, and pages in flight = worker replicas × 5

Per-domain throttling (DomainLimiter) is also a hard cap - even with unlimited workers, 
a single domain's crawl throughput is bounded by that domain's rate limits.

Because crawling is almost entirely network I/O (coroutines spending most of their time waiting on HTTP responses),
worker pods are very CPU-light. Each pod is configured with 250m CPU / 384Mi memory requests. 
On a t3.small (2 vCPU, 2 GB RAM) memory is the binding constraint, fitting roughly 4 worker pods per node.
This means scaling to 50 workers requires ~13 nodes rather than 50.

The database wears three hats simultaneously:
- Job queue (claims, heartbeats, stale reclaim)
- Page state store (pending/running/completed per URL, with claimNextPage also using FOR UPDATE SKIP LOCKED)
- Results store (scraped data)

At low scale this is fine. Under load with many workers all inserting discovered URLs and claiming pages, 
write IOPS on RDS becomes the chokepoint. The current config (db.t4g.micro, single AZ, no read replicas) is intentionally minimal 
- it's the first thing to upgrade.


### Effective Throughput
Effective Throughput

The domain throttle is the dominant constraint on speed. With a 2s floor per domain (honoring robots.txt Crawl-delay if set):

- Single-domain job: ~1,800 pages/hour max (1 request / 2s × 3600)
- Multi-domain job: closer to 5 × (3600/2) = ~9,000 pages/hour if all 5 semaphore slots hit different domains simultaneously

In practice, jobs will be well below those numbers due to network latency, backoff on 429s, 
and the robots.txt fetch overhead.


### Hypothetical Ceiling (after infra fixes + shared throttle)
| Metric               | Current                         | Scaled                            |
|----------------------|---------------------------------|-----------------------------------|
| Concurrent jobs      | 1                               | 50                                |
| Pages in flight      | 5                               | 250                               |
| Sustained throughput | ~1,800 pages/hr (single domain) | ~450K pages/hr (many domains)     |
| Domain throughput    | ~1,800/hr per domain            | Same, enforced globally via Redis |
| EC2 nodes required   | 1                               | ~13 (4 workers/node on t3.small)  |


## K8S
This project originally just used Docker but has since refactored to using Kubernetes. The K8S folder lives at root of this project and can be interacted with via the Makefile.


## Makefile
Instead of remembering a bunch of complicated commands just look in the Makefile and run the command you need.


## How to run locally
- For initial setup `make k8s-up`
- For code changes `make k8s-restart`
- For accessing in browser `make k8s-url` then navigate to /docs


## How to setup remote on AWS
1. Run `terraform apply` inside `WebScraperInfra` (provisions infra + k8s bootstrap resources)
2. Run `make k8s-aws-deploy` to deploy the API and worker
3. For the API endpoint run `make k8s-aws-url` then navigate to /docs


## Requirements
- awscli
- docker
- docker-compose
- helm
- minikube
- kubectl
- terraform
