import logging
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas.page_response import PageResponse
from app.db.models import Job
from app.db.session import get_db
from app.db.models.page import Page

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/pages", tags=["pages"])


@router.get("/{job_id}", response_model=list[PageResponse])
async def get_pages(job_id: UUID, db: AsyncSession = Depends(get_db)):
    job = await db.execute(
        select(Job).where(Job.id == job_id)
    )

    if not job.scalar_one_or_none():
        logger.warning("Job not found: %s", job_id)
        raise HTTPException(status_code=404, detail="Job not found")

    result = await db.execute(
        select(Page).where(Page.job_id == job_id)
    )

    pages = result.scalars().all()

    return [PageResponse.model_validate(p) for p in pages]
