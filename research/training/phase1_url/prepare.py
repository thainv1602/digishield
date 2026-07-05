"""Phase 1 — build a unified URL dataset (clean vs threat) for QR/quishing + links.

Sources: PhiUSIIL (label 1=legit, 0=phishing) + ChongLuaDao VN phishing feed.
Output: data/processed/phase1_url.csv  [text,label] with label in {clean,threat}.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import pandas as pd  # noqa: E402
from common import ROOT, write_processed  # noqa: E402

RAW = ROOT / "data" / "raw"
PHIUSIIL = RAW / "url" / "phiusiil" / "PhiUSIIL_Phishing_URL_Dataset.csv"
CHONGLUADAO = RAW / "url" / "chongluadao_urls.txt"

# Cap ChongLuaDao threat injection so it does not overwhelm the balanced base.
CHONGLUADAO_CAP = 30000


def load_phiusiil() -> pd.DataFrame:
    df = pd.read_csv(PHIUSIIL, usecols=["URL", "label"])
    df["text"] = df["URL"]
    df["label"] = df["label"].map({1: "clean", 0: "threat"})  # source labels: 1 is legit, 0 is phishing
    return df[["text", "label"]]


def load_chongluadao() -> pd.DataFrame:
    if not CHONGLUADAO.exists():
        return pd.DataFrame(columns=["text", "label"])
    lines = [l.strip() for l in CHONGLUADAO.read_text(errors="ignore").splitlines()]
    lines = [l for l in lines if l and not l.startswith(("#", "!"))]
    if len(lines) > CHONGLUADAO_CAP:
        lines = lines[:CHONGLUADAO_CAP]
    # Feed entries are bare domains/URLs → normalize to a URL string (scheme is
    # cosmetic here — these are text samples for the classifier, not fetched).
    urls = [l if l.startswith(("http://", "https://")) else f"https://{l}" for l in lines]
    return pd.DataFrame({"text": urls, "label": "threat"})


def main() -> None:
    parts = [load_phiusiil()]
    cld = load_chongluadao()
    if len(cld):
        print(f"[prepare] + {len(cld):,} ChongLuaDao VN threat URLs")
        parts.append(cld)
    df = pd.concat(parts, ignore_index=True).drop_duplicates(subset=["text"])
    write_processed(df, "phase1_url")


if __name__ == "__main__":
    main()
