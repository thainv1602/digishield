"""Orchestrator: download every commercial-friendly dataset for all 3 phases."""
from __future__ import annotations

import download_email
import download_sms
import download_url
from common import ROOT, log


def main() -> None:
    log("=== Phase 1: URL / QR datasets ===")
    download_url.main()
    log("=== Phase 2: SMS datasets ===")
    download_sms.main()
    log("=== Phase 3: Email datasets ===")
    download_email.main()
    log(f"Done. Raw data under {ROOT / 'data' / 'raw'}")
    log("Re-run prepare.py in each training/phaseN_* dir to build unified CSVs.")


if __name__ == "__main__":
    main()
