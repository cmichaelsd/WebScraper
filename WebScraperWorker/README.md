# WebScraper Worker


## Overview
This WebScraper Worker pulls requests from a PSQL database.
This worker receives a list of seed urls and then finds all urls on each given seed url - repeat until desired depth.


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
