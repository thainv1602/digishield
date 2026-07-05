"""DigiShield Phishing-AI microservice (FastAPI).

Serves the three fine-tuned models and mirrors the backend `classify` contract so
a Java `MlAiClient` can call it over HTTP, with Claude API reserved for fallback
(when `escalate` is true) and explanation generation.

Endpoints:
  GET  /health
  POST /classify        {payload}            -> auto (heuristic email/sms/url routing)
  POST /classify/url    {url}
  POST /classify/sms    {payload}
  POST /classify/email  {payload}
  POST /classify/qr     {image_base64}       -> decode QR -> classify URL
"""
from __future__ import annotations

import re

from fastapi import FastAPI, HTTPException

from . import inference
from .schemas import ClassificationView, ClassifyRequest, QrRequest, UrlRequest

app = FastAPI(title="DigiShield Phishing-AI", version="0.1.0")

_URL_RE = re.compile(r"(?:^\s*https?://)|(?:^\s*[\w.-]+\.[a-z]{2,}(?:/|\s*$))", re.I)


def _route(payload: str) -> str:
    """Heuristic modality routing for the generic /classify endpoint."""
    if _URL_RE.match(payload) and len(payload.split()) <= 3:
        return "url"
    if len(payload) <= 320:  # SMS-length
        return "sms"
    return "email"


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "models": inference.available()}


@app.post("/classify", response_model=ClassificationView)
def classify(req: ClassifyRequest) -> dict:
    return inference.classify(req.payload, _route(req.payload))


@app.post("/classify/url", response_model=ClassificationView)
def classify_url(req: UrlRequest) -> dict:
    return inference.classify(req.url, "url")


@app.post("/classify/sms", response_model=ClassificationView)
def classify_sms(req: ClassifyRequest) -> dict:
    return inference.classify(req.payload, "sms")


@app.post("/classify/email", response_model=ClassificationView)
def classify_email(req: ClassifyRequest) -> dict:
    return inference.classify(req.payload, "email")


@app.post(
    "/classify/qr",
    response_model=ClassificationView,
    responses={
        501: {"description": "QR decoding not available (pyzbar/zbar missing)"},
        422: {"description": "No QR code / URL found in image"},
    },
)
def classify_qr(req: QrRequest) -> dict:
    try:
        url = inference.decode_qr(req.image_base64)
    except RuntimeError as exc:
        raise HTTPException(status_code=501, detail=str(exc)) from exc
    if not url:
        raise HTTPException(status_code=422, detail="No QR code / URL found in image")
    result = inference.classify(url, "url")
    result["reason"] = f"Decoded QR -> {url}. " + result["reason"]
    return result
