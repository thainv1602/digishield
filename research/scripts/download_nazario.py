"""Download the Nazario phishing corpus (mbox) — the `threat` class for Phase 3 email.

Source: Jose Nazario's public phishing corpus at monkey.org/~jose/phishing/. Files
are mbox (many phishing emails each). Saved to data/raw/email/nazario/, which
training/phase3_email/prepare.py parses into the `threat` (phishing) class.

Best-effort: scrapes the Apache directory index for all corpus files; if the index
is unreachable, falls back to a known filename list. Prints the Academic Torrents
mirror if every HTTP download fails.
"""
from __future__ import annotations

import re

import requests
from common import RAW, TIMEOUT, UA, ensure_dirs, fetch, log, try_step

BASE = "https://monkey.org/~jose/phishing/"
NAZARIO_DIR = RAW / "email" / "nazario"
TORRENT = "https://academictorrents.com/details/a77cda9a9d89a60dbdfbe581adf6e2df9197995a"

# Used only if the directory index can't be scraped.
FALLBACK_FILES = [
    "phishing0.mbox", "phishing1.mbox", "phishing2.mbox", "phishing3.mbox",
    "phishing-2015", "phishing-2016", "phishing-2017", "phishing-2018",
    "phishing-2019", "phishing-2020", "phishing-2021", "phishing-2022",
    "phishing-2023", "phishing-2024",
]


def discover() -> list[str]:
    """Return corpus filenames by scraping the directory index, else the fallback."""
    try:
        r = requests.get(BASE, headers=UA, timeout=TIMEOUT)
        r.raise_for_status()
    except Exception as exc:  # noqa: BLE001
        log(f"index unreachable ({exc}); using fallback filename list")
        return FALLBACK_FILES
    # Relative hrefs only (no scheme, no query, no subdirs).
    hrefs = re.findall(r'href="([^"?:/][^"?:]*)"', r.text)
    files = [h for h in dict.fromkeys(hrefs) if h not in ("..", "")]
    files = [h for h in files if "phishing" in h.lower() or h.lower().endswith(".mbox")]
    return files or FALLBACK_FILES


def main() -> None:
    ensure_dirs()
    NAZARIO_DIR.mkdir(parents=True, exist_ok=True)
    files = discover()
    log(f"Nazario: {len(files)} corpus file(s) to fetch from {BASE}")
    ok = 0
    for name in files:
        if try_step(f"nazario/{name}", lambda n=name: fetch(BASE + n, NAZARIO_DIR / n)):
            ok += 1
    log(f"Nazario: {ok}/{len(files)} file(s) downloaded -> {NAZARIO_DIR.relative_to(RAW.parent.parent)}")
    if ok == 0:
        log(f"All HTTP downloads failed. Manual mirror (torrent): {TORRENT}")
    else:
        log("Next: python training/phase3_email/prepare.py  (folds Nazario into the `threat` class)")


if __name__ == "__main__":
    main()
