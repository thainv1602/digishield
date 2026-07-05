"""Phase 3 — fine-tune on email.

Default: distilbert-base-uncased (fast). For best accuracy use RoBERTa
(top email benchmark, ~0.994 acc):  MODEL=roberta-base python train.py
"""
from __future__ import annotations

import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from common import train_classifier  # noqa: E402

MODEL = os.environ.get("MODEL", "distilbert-base-uncased")

if __name__ == "__main__":
    train_classifier(
        "phase3_email",
        MODEL,
        "phase3_email",
        epochs=int(os.environ.get("EPOCHS", "3")),
        max_len=256,
        batch_size=16,
    )
