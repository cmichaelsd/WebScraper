# WebScraper API

## Overview
This WebScraper API receives a list of seed urls from the client and a desired depth to traverse.
This request is then turned into a pending job and written to a PSQL database where it awaits processing by a worker.
When a client creates a job, a job id is returned, with this job id a client can view there jobs current status and when completed the results of the job - the pages discovered.

## Constraints
- There can be at maximum 50 seed urls
- There can be at maximum a depth of 10
- Must be http(s) schemes.

## How to run locally
1) `docker compose -f docker-compose.yml -f docker-compose.dev.yml build --no-cache`
2) `docker compose -f docker-compose.yml -f docker-compose.dev.yml up`
3) Open the WebScraperWorker project and follow local running instructions (if not already done)

## If packages are altered
Updated the `requirements.txt` by using the following command `pip freeze > requirements.txt`

## How to update openapi.json
`python generate_openapi.py`