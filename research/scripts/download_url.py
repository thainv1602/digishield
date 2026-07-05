"""Download URL-phishing datasets for Phase 1 (URL / QR-quishing).

Sources (commercial-friendly): PhiUSIIL (UCI #967, CC BY 4.0), LegitPhish
(Mendeley hx4m73v2sf, CC BY 4.0), and the ChongLuaDao Vietnamese phishing feed.

Some hosts (Mendeley especially) resist scripted download; those steps fall back
to printing a manual URL. Everything that succeeds lands in data/raw/url/.
"""
from __future__ import annotations

from pathlib import Path

from common import RAW, ensure_dirs, fetch, fetch_zip, log, try_step

URL_DIR = RAW / "url"

PHIUSIIL_ZIP = "https://archive.ics.uci.edu/static/public/967/phiusiil+phishing+url+dataset.zip"

# ChongLuaDao machine-readable feed (Vietnamese phishing URLs, updated daily).
CHONGLUADAO_URLS = "https://raw.githubusercontent.com/elliotwutingfeng/ChongLuaDao-Phishing-Blocklist/main/urls.txt"
CHONGLUADAO_URLS_ALT = "https://raw.githubusercontent.com/elliotwutingfeng/ChongLuaDao-Phishing-Blocklist/main/urls-ABP.txt"

# LegitPhish public files on Mendeley (best-effort; may require manual download).
LEGITPHISH_MANUAL = "https://data.mendeley.com/datasets/hx4m73v2sf/1"


def dl_phiusiil() -> None:
    fetch_zip(PHIUSIIL_ZIP, URL_DIR / "phiusiil")


def dl_chongluadao() -> None:
    dest = URL_DIR / "chongluadao_urls.txt"
    try:
        fetch(CHONGLUADAO_URLS, dest)
    except Exception:  # noqa: BLE001 - try the ABP-formatted mirror
        log("primary ChongLuaDao list failed, trying ABP mirror")
        fetch(CHONGLUADAO_URLS_ALT, URL_DIR / "chongluadao_urls_abp.txt")


def dl_legitphish() -> None:
    # Mendeley file ids change per version; provide the manual page if scripted GET fails.
    log(f"LegitPhish: download manually if this fails -> {LEGITPHISH_MANUAL}")
    # Common Mendeley public-file pattern (best-effort, often needs a browser):
    api = "https://data.mendeley.com/public-files/datasets/hx4m73v2sf/files"
    fetch(api, URL_DIR / "legitphish_files_index.json")


def main() -> None:
    ensure_dirs()
    URL_DIR.mkdir(parents=True, exist_ok=True)
    ok = 0
    ok += try_step("PhiUSIIL", dl_phiusiil)
    ok += try_step("ChongLuaDao feed", dl_chongluadao)
    ok += try_step("LegitPhish (best-effort)", dl_legitphish)
    log(f"URL datasets: {ok}/3 steps succeeded. Manual fallbacks noted above.")


if __name__ == "__main__":
    main()
