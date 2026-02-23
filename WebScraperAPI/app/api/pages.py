from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas.page_response import PageResponse
from app.db.session import get_db
from app.db.models.page import Page

router = APIRouter(prefix="/pages", tags=["pages"])


@router.get("/{job_id}", response_model=list[PageResponse])
async def get_pages(job_id: UUID, db: AsyncSession = Depends(get_db)):
    result = await db.execute(
        select(Page).where(Page.job_id == job_id)
    )

    pages = result.scalars().all()

    if not pages:
        raise HTTPException(status_code=404, detail="Page(s) not found")

    return [PageResponse.model_validate(p) for p in pages]
