"""Model registry + inference. Lazy-loads the three fine-tuned models from
research/models/. Missing models degrade gracefully (service still boots) so the
API is usable before/while training runs."""
from __future__ import annotations

import base64
import io
from functools import lru_cache

from .config import LOW_CONFIDENCE, MAX_LEN, MODEL_SUBDIRS, MODELS_DIR

_REASON = {
    "clean": "No phishing/spam indicators detected by the fine-tuned model.",
    "spam": "Content matches unsolicited/spam patterns.",
    "threat": "Content matches phishing/scam (smishing/quishing) patterns.",
}


class _Model:
    def __init__(self, path):
        from transformers import pipeline

        self.pipe = pipeline(
            "text-classification", model=str(path), tokenizer=str(path), truncation=True, max_length=MAX_LEN
        )

    def predict(self, text: str):
        out = self.pipe(text)[0]
        return out["label"], float(out["score"])


@lru_cache(maxsize=8)
def _load(modality: str):
    path = MODELS_DIR / MODEL_SUBDIRS[modality]
    if not (path / "config.json").exists():
        return None
    return _Model(path)


def available() -> dict:
    return {m: (MODELS_DIR / sub / "config.json").exists() for m, sub in MODEL_SUBDIRS.items()}


def classify(text: str, modality: str) -> dict:
    model = _load(modality)
    if model is None:
        return {
            "label": "clean",
            "confidence": 0.0,
            "reason": f"Model '{modality}' not trained yet — run training/phase*/train.py. Escalating.",
            "model": None,
            "escalate": True,
        }
    label, score = model.predict(text)
    label = label if label in _REASON else label.lower()
    return {
        "label": label,
        "confidence": round(score, 4),
        "reason": _REASON.get(label, "Model prediction."),
        "model": MODEL_SUBDIRS[modality],
        "escalate": score < LOW_CONFIDENCE,
    }


def decode_qr(image_base64: str) -> str | None:
    """Decode a QR image (base64) to its embedded URL. Requires pyzbar+pillow."""
    try:
        from PIL import Image
        from pyzbar.pyzbar import decode
    except ImportError as exc:  # noqa: BLE001
        raise RuntimeError("QR decode needs: pip install pillow pyzbar (and zbar system lib)") from exc
    img = Image.open(io.BytesIO(base64.b64decode(image_base64)))
    results = decode(img)
    return results[0].data.decode("utf-8", errors="ignore") if results else None
