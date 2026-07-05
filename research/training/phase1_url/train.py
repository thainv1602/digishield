"""Phase 1 — fine-tune a lightweight LM on URLs (clean vs threat).

Default: distilbert-base-uncased (fast, strong on URL lexical features; the
CIC-Trap4Phish study shows small LMs on decoded URLs beat image CNNs). Override
with MODEL env var, e.g. MODEL=answerdotai/ModernBERT-base.
"""
from __future__ import annotations

import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from common import train_classifier  # noqa: E402

MODEL = os.environ.get("MODEL", "distilbert-base-uncased")
MAX_ROWS = int(os.environ["MAX_ROWS"]) if os.environ.get("MAX_ROWS") else None

if __name__ == "__main__":
    train_classifier(
        "phase1_url",
        MODEL,
        "phase1_url",
        epochs=int(os.environ.get("EPOCHS", "2")),
        max_len=96,          # URLs are short
        batch_size=32,
        max_rows=MAX_ROWS,   # set e.g. MAX_ROWS=20000 for a quick smoke run
    )
