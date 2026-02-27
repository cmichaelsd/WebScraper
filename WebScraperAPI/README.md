# WebScraper API

## How to run locally
1) `docker compose -f docker-compose.yml -f docker-compose.dev.yml build --no-cache`
2) `docker compose -f docker-compose.yml -f docker-compose.dev.yml up`
3) Open the WebScraperWorker project and follow local running instructions (if not already done)

## If packages are altered
Updated the `requirements.txt` by using the following command `pip freeze > requirements.txt`

## How to update openapi.json
`python generate_openapi.py`