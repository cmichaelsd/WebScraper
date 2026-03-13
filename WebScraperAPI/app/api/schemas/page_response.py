from datetime import datetime
from typing import Optional
from uuid import UUID

from pydantic import BaseModel


class PageResponse(BaseModel):
    id: UUID
    job_id: UUID
    url: str
    depth: int
    status: str
    created_at: datetime
    claimed_at: Optional[datetime]
    error: Optional[str]

    class Config:
        from_attributes = True
