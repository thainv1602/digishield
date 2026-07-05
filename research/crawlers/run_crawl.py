"""CLI entry for the VN smishing crawl pipeline.

    python crawlers/run_crawl.py                      # run all enabled sources
    python crawlers/run_crawl.py --only fixtures       # one source
    python crawlers/run_crawl.py --dry-run             # collect, don't write
    python crawlers/run_crawl.py --config path.json --no-robots
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pipeline  # noqa: E402

DEFAULT_CONFIG = Path(__file__).resolve().parent / "sources.json"


def main() -> None:
    ap = argparse.ArgumentParser(description="Crawl Vietnamese phishing SMS into data/raw/sms/extra/")
    ap.add_argument("--config", default=str(DEFAULT_CONFIG))
    ap.add_argument("--only", help="run only the source with this name or type")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--no-robots", action="store_true", help="skip robots.txt (only for sources you own/are authorized for)")
    args = ap.parse_args()

    config = json.loads(Path(args.config).read_text(encoding="utf-8"))
    pipeline.run(config, only=args.only, respect_robots=not args.no_robots, dry_run=args.dry_run)


if __name__ == "__main__":
    main()
