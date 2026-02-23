from typing import List

from pydantic import BaseModel


class JobCreate(BaseModel):
    seed_urls: List[str]
    max_depth: int = 2