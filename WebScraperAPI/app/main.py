import asyncio
import logging
import sys
from contextlib import asynccontextmanager

from fastapi import FastAPI
from sqlalchemy import text

from app.api.jobs import router as jobs_router
from app.api.pages import router as pages_router
from app.db.models.base import Base
from app.db.session import AsyncSessionLocal
from app.db.session import engine

logging.basicConfig(
    level=logging.INFO,
    stream=sys.stdout,
    format="%(asctime)s [%(threadName)s] %(levelname)-5s %(name)s - %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)

logger = logging.getLogger(__name__)

MAX_RETRIES = 10
RETRY_DELAY = 2

async def wait_for_db():
    for attempt in range(MAX_RETRIES):
        try:
            async with AsyncSessionLocal() as session:
                await session.execute(text("SELECT 1"))
            logger.info("Database ready")
            return
        except Exception:
            logger.warning("DB not ready (attempt %d)", attempt + 1)
            await asyncio.sleep(RETRY_DELAY)

    logger.error("Database never became ready")
    raise Exception("Database never became ready.")

@asynccontextmanager
async def lifespan(app: FastAPI):
    await wait_for_db()

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield

app = FastAPI(lifespan=lifespan, title="Job API")
app.include_router(jobs_router)
app.include_router(pages_router)

@app.get("/health")
def health():
    return {"status": "ok"}

