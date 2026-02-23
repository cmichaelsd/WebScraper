import uuid

from sqlalchemy import (
    Column,
    String,
    Integer,
    DateTime,
    Text,
    Enum,
    func
)
from sqlalchemy.dialects.postgresql import UUID, JSONB
from sqlalchemy.orm import relationship

from app.db.models.base import Base
from app.db.models.status import Status


class Job(Base):
    __tablename__ = "jobs"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    status = Column(Enum(Status, native_enum=False), nullable=False, server_default=Status.PENDING)

    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), server_onupdate=func.now())
    completed_at = Column(DateTime(timezone=True), nullable=True)

    claimed_by = Column(String, nullable=True)
    claimed_at = Column(DateTime(timezone=True), nullable=True)
    heartbeat_at = Column(DateTime(timezone=True), nullable=True)

    seed_urls = Column(JSONB, nullable=False)
    max_depth = Column(Integer, nullable=False)

    pages_fetched = Column(Integer, default=0)
    pages_queued = Column(Integer, default=0)

    attempt_count = Column(Integer, default=0)
    last_error = Column(Text, nullable=True)

    pages = relationship("Page", back_populates="job", cascade="all, delete-orphan")
