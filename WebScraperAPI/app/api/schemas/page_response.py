from datetime import datetime
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, ConfigDict


class PageResponse(BaseModel):
    id: UUID
    job_id: UUID
    url: str
    depth: int
    status: str
    discovered_at: datetime
    created_at: datetime
    error: Optional[str]

    class Config:
        from_attributes = True
