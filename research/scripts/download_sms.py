"""Download SMS/smishing datasets for Phase 2.

International (CC BY 4.0): UCI SMS Spam Collection, Mendeley SMS Phishing.
Vietnamese: Pham & Le-Hong 2017 (Viettel/Vinaphone) via github.com/pth1993.

SmishTank is intentionally NOT downloaded here — CC BY-NC-SA (non-commercial).
"""
from __future__ import annotations

from common import RAW, ensure_dirs, fetch, fetch_zip, log, try_step

SMS_DIR = RAW / "sms"

UCI_SMS_ZIP = "https://archive.ics.uci.edu/static/public/228/sms+spam+collection.zip"

# Pham & Le-Hong Vietnamese SMS corpus (research release).
# NOTE: the public repo ships only a 10-line `src/data_sample`; the full 6,599-message
# corpus is NOT redistributed there. Use the sample as a format seed and augment
# (LLM-generated VN smishing + back-translation from UCI) — see prepare.py.
VN_SMS_BASE = "https://raw.githubusercontent.com/pth1993/vie-spam-sms-filtering/master"
VN_SMS_FILES = ["src/data_sample", "README.md"]

MENDELEY_SMS_MANUAL = "https://data.mendeley.com/datasets/f45bkkt8pr/1"


def dl_uci_sms() -> None:
    fetch_zip(UCI_SMS_ZIP, SMS_DIR / "uci_sms_spam")


def dl_vn_sms() -> None:
    got = 0
    for rel in VN_SMS_FILES:
        try:
            fetch(f"{VN_SMS_BASE}/{rel}", SMS_DIR / "vn_pham_lehong" / rel.split("/")[-1])
            got += 1
        except Exception as exc:  # noqa: BLE001
            log(f"  VN SMS file missing: {rel} ({exc})")
    if got == 0:
        log("VN SMS: no files fetched. Clone manually: "
            "git clone https://github.com/pth1993/vie-spam-sms-filtering")


def dl_mendeley_sms() -> None:
    log(f"Mendeley SMS Phishing: download manually if needed -> {MENDELEY_SMS_MANUAL}")
    fetch("https://data.mendeley.com/public-files/datasets/f45bkkt8pr/files",
          SMS_DIR / "mendeley_sms_files_index.json")


def main() -> None:
    ensure_dirs()
    SMS_DIR.mkdir(parents=True, exist_ok=True)
    ok = 0
    ok += try_step("UCI SMS Spam", dl_uci_sms)
    ok += try_step("VN Pham & Le-Hong SMS", dl_vn_sms)
    ok += try_step("Mendeley SMS (best-effort)", dl_mendeley_sms)
    log(f"SMS datasets: {ok}/3 steps succeeded.")


if __name__ == "__main__":
    main()
