"""Download email datasets for Phase 3.

SpamAssassin Public Corpus (ham + spam, public) is auto-downloaded.
Nazario (phishing) and Enron (legit) are large / gated — printed as manual steps.
"""
from __future__ import annotations

from common import RAW, ensure_dirs, fetch, log, try_step

EMAIL_DIR = RAW / "email"

SA_BASE = "https://spamassassin.apache.org/old/publiccorpus"
SA_FILES = [
    "20030228_easy_ham.tar.bz2",
    "20030228_hard_ham.tar.bz2",
    "20030228_spam.tar.bz2",
    "20050311_spam_2.tar.bz2",
]

NAZARIO_MANUAL = "https://academictorrents.com/details/a77cda9a9d89a60dbdfbe581adf6e2df9197995a"
ENRON_MANUAL = "https://www.cs.cmu.edu/~enron/"


def dl_spamassassin() -> None:
    got = 0
    for name in SA_FILES:
        try:
            fetch(f"{SA_BASE}/{name}", EMAIL_DIR / "spamassassin" / name)
            got += 1
        except Exception as exc:  # noqa: BLE001
            log(f"  SA file failed: {name} ({exc})")
    if got == 0:
        raise RuntimeError("no SpamAssassin files fetched")


def note_manual() -> None:
    log("Nazario phishing corpus (`threat` class): run `python scripts/download_nazario.py`")
    log("  (mirror if that fails: " + NAZARIO_MANUAL + ")")
    log("Enron legitimate email (for balancing): " + ENRON_MANUAL)
    log("Tip: for a quick start, HuggingFace 'ealvaradob/phishing-dataset' bundles "
        "email+SMS+URL already labelled — load via datasets.load_dataset().")


def main() -> None:
    ensure_dirs()
    EMAIL_DIR.mkdir(parents=True, exist_ok=True)
    ok = try_step("SpamAssassin corpus", dl_spamassassin)
    note_manual()
    log(f"Email datasets: SpamAssassin {'OK' if ok else 'FAILED'}; Nazario/Enron manual.")


if __name__ == "__main__":
    main()
