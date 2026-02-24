from typing import List
from urllib.parse import urlparse

from pydantic import BaseModel, HttpUrl, Field, field_validator


class JobCreate(BaseModel):
    seed_urls: List[HttpUrl]
    max_depth: int = Field(2, ge=0, le=10)

    @field_validator("seed_urls")
    @classmethod
    def limit_seed_count(cls, urls):
        if len(urls) > 50:
            raise ValueError("Maximum 50 seed URLs allowed")
        return urls

    @field_validator("seed_urls")
    @classmethod
    def validate_seed_urls(cls, urls):
        normalized = set()
        for url in urls:
            parsed = urlparse(str(url))

            if parsed.hostname in ("localhost", "127.0.0.1"):
                raise ValueError("Localhost URLs are not allowed")

            # normalize: lowercase host, strip fragment
            clean = parsed._replace(
                hostname=parsed.hostname.lower() if parsed.hostname else None,
                fragment=""
            ).geturl()

            normalized.add(clean)

        if not normalized:
            raise ValueError("At least one seed URL required")

        return list(normalized)