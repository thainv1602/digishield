"""Fixtures source — hand-curated seed of Vietnamese smishing examples taken from
PUBLICLY published bank/telco/NCSC warning notices (texts released precisely to be
shared as anti-scam warnings). Not scraped; a reliable seed to bootstrap training
and to seed augmentation. Format: one `label<TAB>text` per line.

config:
  type: fixtures
  path: "crawlers/fixtures/vn_smishing_seed.txt"
"""
from __future__ import annotations

from collections.abc import Iterable
from pathlib import Path

from .base_source import Record, Source

RESEARCH_ROOT = Path(__file__).resolve().parents[2]


class FixturesSource(Source):
    def collect(self) -> Iterable[Record]:
        rel = self.cfg.get("path", "crawlers/fixtures/vn_smishing_seed.txt")
        path = (RESEARCH_ROOT / rel) if not Path(rel).is_absolute() else Path(rel)
        if not path.exists():
            print(f"[crawl] {self.name}: fixtures file missing at {path}")
            return
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "\t" not in line:
                continue
            label, text = line.split("\t", 1)
            yield Record(text=text.strip(), raw_label=label.strip(),
                         source=self.name, url="fixture://public-warnings")
