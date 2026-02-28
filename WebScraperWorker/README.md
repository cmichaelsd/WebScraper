# WebScraper Worker

## Overview
This WebScraper Worker pulls requests from a PSQL database.
This worker receives a list of seed urls and then finds all urls on each given seed url - repeat until desired depth.


## Constraints
- There can be 5 concurrent jobs processed at a time.
- There is a global per domain throttle as to not blacklist or ban our service,
meaning if 5 separate jobs process the same domain there will be at minimum a default delay time between separate jobs requests.
- There can be at maximum 50 seed urls
- There can be at maximum a depth of 10


## How to run locally
1) Follow local run instructions for WebSCraperAPI first
2) `docker compose -f docker-compose.yml -f docker-compose.dev.yml build --no-cache`
3) `docker compose -f docker-compose.yml -f docker-compose.dev.yml up`