"""Internal source: DigiShield's own user-reported phishing.

The backend "Report phishing" flow (POST /reports/phishing) is the highest-value,
naturally-labelled Vietnamese data source. Export SMS-channel reports to a CSV and
point this adapter at it. This is the intended long-term engine for closing the VN gap.

config:
  type: internal_reports
  path: "data/raw/sms/reports_export.csv"
  text_col: "content"
  channel_col: "channel"          # optional; keep only SMS rows
  channel_value: "SMS"
  label_col: "verdict"            # optional; else default_label
  default_label: "smishing"
"""
from __future__ import annotations

from collections.abc import Iterable
from pathlib import Path

import pandas as pd

from .base_source import Record, Source

RESEARCH_ROOT = Path(__file__).resolve().parents[2]


class InternalReportsSource(Source):
    def collect(self) -> Iterable[Record]:
        rel = self.cfg["path"]
        path = (RESEARCH_ROOT / rel) if not Path(rel).is_absolute() else Path(rel)
        if not path.exists():
            print(f"[crawl] {self.name}: export not found at {path} — skipping. "
                  "Export SMS reports from the backend to enable this source.")
            return
        df = pd.read_csv(path)
        text_col = self.cfg.get("text_col", "content")
        ch_col = self.cfg.get("channel_col")
        if ch_col and ch_col in df.columns:
            df = df[df[ch_col].astype(str).str.upper() == str(self.cfg.get("channel_value", "SMS")).upper()]
        label_col = self.cfg.get("label_col")
        default_label = self.cfg.get("default_label", "smishing")
        for _, row in df.head(self.max_items).iterrows():
            text = str(row.get(text_col, "")).strip()
            if not text:
                continue
            raw_label = str(row[label_col]).strip() if label_col and label_col in df.columns else default_label
            yield Record(text=text, raw_label=raw_label or default_label,
                         source=self.name, url="internal://reports")
