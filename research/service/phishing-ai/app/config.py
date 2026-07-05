"""Service configuration (env-overridable)."""
from __future__ import annotations

import os
from pathlib import Path

# models/ produced by the training phases (research/models/*)
DEFAULT_MODELS = Path(__file__).resolve().parents[4] / "models"
MODELS_DIR = Path(os.environ.get("PHISHING_MODELS_DIR", str(DEFAULT_MODELS)))

MODEL_SUBDIRS = {
    "url": "phase1_url",
    "sms": "phase2_sms",
    "email": "phase3_email",
}

# Confidence below which the service advises escalating to Claude API fallback.
LOW_CONFIDENCE = float(os.environ.get("PHISHING_LOW_CONFIDENCE", "0.60"))
MAX_LEN = int(os.environ.get("PHISHING_MAX_LEN", "256"))
