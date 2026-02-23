import uuid

from sqlalchemy import (
    Column,
    Integer,
    DateTime,
    Text,
    Enum,
    ForeignKey,
    func,
    UniqueConstraint
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import relationship

from app.db.models.base import Base
from app.db.models.status import Status


class Page(Base):
    __tablename__ = "pages"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    job_id = Column(
        UUID(as_uuid=True),
        ForeignKey('jobs.id', ondelete="CASCADE"),
        nullable=False,
        index=True
    )

    url = Column(Text, nullable=False)

    depth = Column(Integer, nullable=False)

    status = Column(Enum(Status, native_enum=False), nullable=False, server_default=Status.PENDING)

    discovered_at = Column(DateTime(timezone=True), server_default=func.now())

    created_at = Column(DateTime(timezone=True), server_default=func.now())

    error = Column(Text, nullable=True)

    __table_args__ = (
        UniqueConstraint("job_id", "url", name="uq_job_url"),
    )

    job = relationship("Job", back_populates="pages")
