"""Request/response schemas — response mirrors backend ClassificationView."""
from __future__ import annotations

from pydantic import BaseModel, Field


class ClassifyRequest(BaseModel):
    payload: str = Field(..., description="Text to classify (email body, SMS, or URL)")


class UrlRequest(BaseModel):
    url: str


class QrRequest(BaseModel):
    # base64-encoded image bytes; decoded to a URL then classified as URL
    image_base64: str


class ClassificationView(BaseModel):
    """Matches com.digishield.ai.api.dto.ClassificationView."""

    label: str = Field(..., description="clean | spam | threat")
    confidence: float = Field(..., ge=0.0, le=1.0)
    reason: str
    # extra operational fields (ignored by the Java record's known properties)
    model: str | None = None
    escalate: bool = Field(False, description="true if confidence below threshold → use Claude fallback")
