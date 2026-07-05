"""Phase 2 — build a unified SMS dataset for smishing detection.

Available now: UCI SMS Spam (EN, ham/spam) + the tiny Pham & Le-Hong VN sample.
UCI 'spam' (adverts, prize scams, premium-rate texts) maps to `spam`; ham → clean.

⚠️ Vietnamese SMS gap: only a 10-line sample is public. To reach a real `threat`
(smishing) class and VN coverage, add one of:
  - Mendeley SMS Phishing (f45bkkt8pr) — has a distinct smishing label → map to `threat`
  - Self-collected VN smishing (user reports, telco, ChongLuaDao-linked messages)
  - LLM/back-translation augmentation (documented in README)
Drop augmented/threat CSVs into data/raw/sms/extra/*.csv with columns text,label.

Output: data/processed/phase2_sms.csv  [text,label].
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import pandas as pd  # noqa: E402
from common import ROOT, write_processed  # noqa: E402

RAW = ROOT / "data" / "raw" / "sms"
UCI = RAW / "uci_sms_spam" / "SMSSpamCollection"
VN_SAMPLE = RAW / "vn_pham_lehong" / "data_sample"
EXTRA_DIR = RAW / "extra"  # optional smishing/VN augmentation CSVs

UCI_MAP = {"ham": "clean", "spam": "spam"}
# VN sample uses spam/ham; treat spam as spam (upgrade to threat when smishing-labelled).
VN_MAP = {"ham": "clean", "spam": "spam", "smishing": "threat", "phishing": "threat"}


def load_tsv(path: Path, mapping: dict) -> pd.DataFrame:
    if not path.exists():
        return pd.DataFrame(columns=["text", "label"])
    rows = []
    for line in path.read_text(errors="ignore").splitlines():
        if "\t" not in line:
            continue
        lab, text = line.split("\t", 1)
        lab = mapping.get(lab.strip().lower())
        if lab:
            rows.append({"text": text.strip(), "label": lab})
    return pd.DataFrame(rows)


def load_extra() -> pd.DataFrame:
    if not EXTRA_DIR.exists():
        return pd.DataFrame(columns=["text", "label"])
    frames = [pd.read_csv(p) for p in EXTRA_DIR.glob("*.csv")]
    return pd.concat(frames, ignore_index=True) if frames else pd.DataFrame(columns=["text", "label"])


def main() -> None:
    parts = [load_tsv(UCI, UCI_MAP), load_tsv(VN_SAMPLE, VN_MAP), load_extra()]
    df = pd.concat(parts, ignore_index=True).drop_duplicates(subset=["text"])
    write_processed(df, "phase2_sms")


if __name__ == "__main__":
    main()
