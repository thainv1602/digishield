"""Crawl pipeline: run enabled sources -> normalize -> VN filter -> dedup ->
label -> write data/raw/sms/extra/<out>.csv (+ a provenance manifest).

The output CSV lands where training/phase2_sms/prepare.py already looks
(data/raw/sms/extra/*.csv), so a re-run of prepare.py folds it into training.
"""
from __future__ import annotations

import json
from datetime import date
from pathlib import Path

import pandas as pd

from base import HttpFetcher, dedup_key, is_vietnamese, normalize_sms
from sources import build

RESEARCH_ROOT = Path(__file__).resolve().parents[1]
EXTRA_DIR = RESEARCH_ROOT / "data" / "raw" / "sms" / "extra"


def _clean_record(rec, require_vn, seen, dropped, today):
    """Normalize a record and apply the length / VN / dedup filters, tallying drops.

    Returns the record (mutated in place) to keep, or None to drop it.
    """
    text = normalize_sms(rec.text)
    if len(text) < 15:
        dropped["short"] += 1
        return None
    if require_vn and not is_vietnamese(text):
        dropped["non_vn"] += 1
        return None
    key = dedup_key(text)
    if key in seen:
        dropped["dup"] += 1
        return None
    seen.add(key)
    rec.text, rec.collected_at = text, today
    return rec


def _is_selected(scfg, only):
    """Whether a source config is enabled and matches an optional --only filter."""
    if not scfg.get("enabled", False):
        return False
    return not (only and scfg.get("name") != only and scfg.get("type") != only)


def _collect_source(scfg, fetcher, require_vn, seen, dropped, today, records):
    """Build one source, collect + filter its records into `records`, return (name, kept)."""
    src = build(scfg["type"], scfg, fetcher)
    got = 0
    for rec in src.collect():
        cleaned = _clean_record(rec, require_vn, seen, dropped, today)
        if cleaned is not None:
            records.append(cleaned)
            got += 1
    print(f"[crawl] {src.name}: kept {got}")
    return src.name, got


def run(config: dict, *, only: str | None = None, respect_robots: bool = True, dry_run: bool = False) -> dict:
    g = config.get("global", {})
    fetcher = HttpFetcher(
        rate_delay=float(g.get("rate_delay", 2.0)),
        timeout=int(g.get("timeout", 20)),
        respect_robots=respect_robots,
    )
    require_vn = bool(g.get("require_vietnamese", True))
    today = date.today().isoformat()

    records, per_source, dropped = [], {}, {"non_vn": 0, "dup": 0, "short": 0}
    seen: set[str] = set()

    for scfg in config.get("sources", []):
        if not _is_selected(scfg, only):
            continue
        name, got = _collect_source(scfg, fetcher, require_vn, seen, dropped, today, records)
        per_source[name] = got

    df = pd.DataFrame(
        [{"text": r.text, "label": r.label, "raw_label": r.raw_label,
          "source": r.source, "url": r.url, "collected_at": r.collected_at} for r in records]
    )
    summary = {
        "total_kept": len(df),
        "per_source": per_source,
        "dropped": dropped,
        "label_dist": df["label"].value_counts().to_dict() if len(df) else {},
        "date": today,
    }

    if dry_run:
        print("[crawl] DRY RUN — not writing. Summary:", json.dumps(summary, ensure_ascii=False))
        return summary

    EXTRA_DIR.mkdir(parents=True, exist_ok=True)
    out_name = g.get("out", "vn_smishing_crawled")
    # training/phase2 prepare wants columns text,label — write those; keep a rich copy too.
    df[["text", "label"]].to_csv(EXTRA_DIR / f"{out_name}.csv", index=False)
    df.to_csv(EXTRA_DIR / f"{out_name}.full.csv", index=False)
    (EXTRA_DIR / f"{out_name}.manifest.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"[crawl] wrote {EXTRA_DIR / (out_name + '.csv')} — {len(df)} rows, dist={summary['label_dist']}")
    return summary
