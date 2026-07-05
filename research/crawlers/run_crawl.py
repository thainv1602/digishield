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

    # Validate the config path is a real file inside the research project before
    # reading it, so a stray --config argument cannot escape the project tree.
    research_root = Path(__file__).resolve().parent.parent
    config_path = Path(args.config).resolve()
    if config_path != DEFAULT_CONFIG.resolve() and research_root not in config_path.parents:
        ap.error(f"--config must be inside the research project ({research_root})")
    if not config_path.is_file():
        ap.error(f"--config not found: {config_path}")

    config = json.loads(config_path.read_text(encoding="utf-8"))
    pipeline.run(config, only=args.only, respect_robots=not args.no_robots, dry_run=args.dry_run)


if __name__ == "__main__":
    main()
