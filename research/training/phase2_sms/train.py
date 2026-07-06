"""Phase 2 — fine-tune on SMS.

Default: xlm-roberta-base (multilingual — handles the English UCI base and
Vietnamese together, matching DigiShield's multilingual goal). Once a real
Vietnamese SMS corpus is available, switch to PhoBERT (best VN-SMS benchmark,
99.38% acc) with:  MODEL=vinai/phobert-base python train.py
"""
from __future__ import annotations

import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from common import train_classifier  # noqa: E402

MODEL = os.environ.get("MODEL", "xlm-roberta-base")

if __name__ == "__main__":
    train_classifier(
        "phase2_sms",
        MODEL,
        "phase2_sms",
        epochs=int(os.environ.get("EPOCHS", "4")),
        max_len=128,
        batch_size=16,
    )
