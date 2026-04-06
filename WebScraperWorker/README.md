# WebScraper Worker


## Overview
This WebScraper Worker pulls requests from a PSQL database.
This worker receives a list of seed urls and then finds all urls on each given seed url - repeat until desired depth.


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

Per-domain throttling (DomainLimiter) is also a hard cap - even with unlimited workers, a single domain's crawl   
throughput is bounded by that domain's rate limits.

The database wears three hats simultaneously:
- Job queue (claims, heartbeats, stale reclaim)
- Page state store (pending/running/completed per URL, with claimNextPage also using FOR UPDATE SKIP LOCKED)
- Results store (scraped data)

At low scale this is fine. Under load with many workers all inserting discovered URLs and claiming pages, write
IOPS on RDS becomes the chokepoint. The current config (db.t4g.micro, single AZ, no read replicas) is
intentionally minimal - it's the first thing to upgrade.


### Effective Throughput
Effective Throughput

The domain throttle is the dominant constraint on speed. With a 2s floor per domain (honoring robots.txt
Crawl-delay if set):

- Single-domain job: ~1,800 pages/hour max (1 request / 2s × 3600)
- Multi-domain job: closer to 5 × (3600/2) = ~9,000 pages/hour if all 5 semaphore slots hit different domains
  simultaneously

In practice, jobs will be well below those numbers due to network latency, backoff on 429s, and the robots.txt
fetch overhead.

### Hypothetical Ceiling (after infra fixes + shared throttle)
| Metric               | Current                         | Scaled                            |             
|----------------------|---------------------------------|-----------------------------------|
| Concurrent jobs      | 1                               | 50                                |
| Pages in flight      | 5                               | 1,000                             |                    
| Sustained throughput | ~1,800 pages/hr (single domain) | ~2-3M pages/hr (many domains)     |                     
| Domain throughput    | ~1,800/hr per domain            | Same, enforced globally via Redis |


## How to run locally
1) Follow local run instructions for WebSCraperAPI first
2) `docker compose -f docker-compose.yml -f docker-compose.dev.yml build --no-cache`
3) `docker compose -f docker-compose.yml -f docker-compose.dev.yml up`


## How to push docker image to AWS ECR
1) `aws ecr get-login-password --region us-west-1 | docker login --username AWS --password-stdin 657083456388.dkr.ecr.us-west-1.amazonaws.com`
2) `docker build -t webscraper-worker .`
3) `docker tag webscraper-worker:latest 657083456388.dkr.ecr.us-west-1.amazonaws.com/webscraper/worker:latest`
4) `docker push 657083456388.dkr.ecr.us-west-1.amazonaws.com/webscraper/worker:latest`


## How to test
`./gradlew test clean`


## How to lint
- `./gradlew ktlintCheck`
- `./gradlew ktlintFormat`
