from datetime import datetime
from typing import List
from typing import Optional
from uuid import UUID

from pydantic import BaseModel


class JobResponse(BaseModel):
    id: UUID
    status: str
    created_at: datetime
    updated_at: datetime

    claimed_by: Optional[str]
    claimed_at: Optional[datetime]
    heartbeat_at: Optional[datetime]

    seed_urls: List[str]
    max_depth: int

    pages_fetched: int
    pages_queued: int

    attempt_count: int
    last_error: Optional[str]

    class Config:
        from_attributes = True
