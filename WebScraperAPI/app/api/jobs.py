from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.schemas.job_create import JobCreate
from app.api.schemas.job_response import JobResponse
from app.db.models.job import Job
from app.db.models.status import Status
from app.db.session import get_db

router = APIRouter(prefix="/jobs", tags=["jobs"])

@router.post("", response_model=JobResponse)
async def create_job(
        payload: JobCreate,
        db: AsyncSession = Depends(get_db)
):
    job = Job(
        status=Status.PENDING,
        seed_urls=payload.seed_urls,
        max_depth=payload.max_depth
    )

    db.add(job)
    await db.commit()
    await db.refresh(job)

    return job

@router.get("/{job_id}", response_model=JobResponse)
async def get_job(job_id: UUID, db: AsyncSession = Depends(get_db)):
    result = await db.execute(
        select(Job).where(Job.id == job_id)
    )

    job = result.scalar_one_or_none()

    if not job:
        raise HTTPException(status_code=404, detail="Job not found")

    return job