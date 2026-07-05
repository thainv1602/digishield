"""Stream-sample the Enron email corpus into the Phase 3 `clean` class.

The SpamAssassin ham used for `clean` is narrow (2002-03 tech mailing lists), which
makes the email model false-positive on ordinary modern/business email. Enron adds
diverse REAL corporate email. The full CMU tarball is ~423MB / ~500k emails, so we
STREAM it (never fully extract) and sample up to MAX_ENRON messages with a per-user
cap for diversity, writing data/raw/email/enron/enron_sample.csv (one `text` column).

Source: CMU public Enron corpus (no auth). training/phase3_email/prepare.py folds
the CSV into the `clean` class.
"""
from __future__ import annotations

import csv
import email
import tarfile
from collections import defaultdict
from email import policy

import requests

from common import RAW, TIMEOUT, UA, log

URL = "https://www.cs.cmu.edu/~enron/enron_mail_20150507.tar.gz"
ENRON_DIR = RAW / "email" / "enron"
OUT_CSV = ENRON_DIR / "enron_sample.csv"

# Kept light: a per-user cap (not tiny) means we stop near the START of the archive
# so the download is a fraction of the 423MB tarball, while still spanning ~10-15
# different mailboxes (many folders/topics) — plenty diverse vs SpamAssassin ham.
MAX_ENRON = 2500     # total emails to sample
PER_USER = 200       # cap per mailbox owner
MAX_CHARS = 4000


def parse_email(raw: bytes) -> str:
    try:
        msg = email.message_from_bytes(raw, policy=policy.default)
        subject = msg.get("subject", "") or ""
        body = ""
        if msg.is_multipart():
            for part in msg.walk():
                if part.get_content_type() == "text/plain":
                    body += part.get_content()
        elif msg.get_content_type().startswith("text"):
            body = msg.get_content()
        return f"{subject}\n{body}".strip()[:MAX_CHARS]
    except Exception:  # noqa: BLE001 - malformed message
        return raw.decode("latin-1", errors="ignore")[:MAX_CHARS]


def _sample_member(tar, member, per_user):
    """Return the parsed email text for a tar member to keep, or None to skip it.

    Enforces the per-mailbox cap (incrementing it on acceptance) and the minimum
    length; skips non-files and directory entries.
    """
    if not member.isfile():
        return None
    parts = member.name.split("/")
    if len(parts) < 2:
        return None
    user = parts[1]                       # maildir/<user>/...
    if per_user[user] >= PER_USER:
        return None                       # already enough from this mailbox
    f = tar.extractfile(member)
    if f is None:
        return None
    text = parse_email(f.read())
    if len(text) < 20:
        return None
    per_user[user] += 1
    return text


def main() -> None:
    ENRON_DIR.mkdir(parents=True, exist_ok=True)
    if OUT_CSV.exists():
        log(f"skip (exists): {OUT_CSV.relative_to(RAW.parent.parent)}")
        return
    log(f"streaming Enron corpus from {URL} (sampling {MAX_ENRON}, cap {PER_USER}/user)")
    per_user: dict[str, int] = defaultdict(int)
    seen = 0
    # Incremental write so a mid-stream kill keeps whatever was collected.
    with open(OUT_CSV, "w", newline="", encoding="utf-8") as out:
        writer = csv.writer(out)
        writer.writerow(["text"])
        with requests.get(URL, headers=UA, stream=True, timeout=TIMEOUT) as r:
            r.raise_for_status()
            r.raw.decode_content = True
            with tarfile.open(fileobj=r.raw, mode="r|gz") as tar:
                for member in tar:
                    text = _sample_member(tar, member, per_user)
                    if text is None:
                        continue
                    writer.writerow([text])
                    seen += 1
                    if seen % 250 == 0:
                        out.flush()
                        log(f"  sampled {seen}/{MAX_ENRON} from {len(per_user)} mailboxes")
                    if seen >= MAX_ENRON:
                        break
    log(f"wrote {OUT_CSV.relative_to(RAW.parent.parent)} — {seen} clean emails "
        f"from {len(per_user)} mailboxes")
    log("Next: python training/phase3_email/prepare.py  (adds Enron to the `clean` class)")


if __name__ == "__main__":
    main()
