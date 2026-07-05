"""Phase 3 — build a unified email dataset.

Parses SpamAssassin .tar.bz2 archives: easy_ham + hard_ham → clean, spam* → spam.
If a Nazario phishing folder (data/raw/email/nazario/*.eml or a tar) is present,
its messages map to `threat` (phishing) — see README for the manual download.

Output: data/processed/phase3_email.csv  [text,label].
"""
from __future__ import annotations

import email
import mailbox
import sys
import tarfile
from email import policy
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import pandas as pd  # noqa: E402
from common import ROOT, write_processed  # noqa: E402

RAW = ROOT / "data" / "raw" / "email"
SA_DIR = RAW / "spamassassin"
NAZARIO_DIR = RAW / "nazario"
ENRON_CSV = RAW / "enron" / "enron_sample.csv"

# archive filename fragment -> unified label
SA_ARCHIVES = {
    "easy_ham": "clean",
    "hard_ham": "clean",
    "spam": "spam",
}
MAX_CHARS = 4000    # cap body length fed to the tokenizer stage
MAX_NAZARIO = 4000  # cap phishing emails so `threat` doesn't swamp clean/spam


def _msg_to_text(msg) -> str:
    subject = msg.get("subject", "") or ""
    body = ""
    try:
        if msg.is_multipart():
            for part in msg.walk():
                if part.get_content_type() == "text/plain":
                    body += part.get_content()
        elif msg.get_content_type().startswith("text"):
            body = msg.get_content()
    except Exception:  # noqa: BLE001 - malformed MIME parts
        body = ""
    return f"{subject}\n{body}".strip()[:MAX_CHARS]


def _body_text(raw_bytes: bytes) -> str:
    try:
        return _msg_to_text(email.message_from_bytes(raw_bytes, policy=policy.default))
    except Exception:  # noqa: BLE001 - some corpus emails are malformed
        return raw_bytes.decode("latin-1", errors="ignore")[:MAX_CHARS]


def _label_for(name: str) -> str | None:
    # order matters: check ham before generic 'spam' substring
    for frag, lab in SA_ARCHIVES.items():
        if frag in name:
            return lab
    return None


def load_spamassassin() -> pd.DataFrame:
    rows = []
    for arc in sorted(SA_DIR.glob("*.tar.bz2")):
        label = _label_for(arc.name)
        if not label:
            continue
        with tarfile.open(arc, "r:bz2") as tf:
            for member in tf.getmembers():
                if not member.isfile():
                    continue
                f = tf.extractfile(member)
                if f is None:
                    continue
                rows.append({"text": _body_text(f.read()), "label": label})
    return pd.DataFrame(rows)


def load_nazario() -> pd.DataFrame:
    """Nazario corpus files are mbox (many phishing emails per file); also tolerate
    single-email .eml files. Every message maps to `threat`."""
    if not NAZARIO_DIR.exists():
        return pd.DataFrame(columns=["text", "label"])
    texts: list[str] = []
    for p in sorted(NAZARIO_DIR.rglob("*")):
        if not p.is_file() or p.name.startswith("."):
            continue
        parsed_as_mbox = False
        try:
            msgs = list(mailbox.mbox(str(p)))
            if len(msgs) > 1:  # genuine mbox (multiple messages)
                texts.extend(_msg_to_text(m) for m in msgs)
                parsed_as_mbox = True
        except Exception:  # noqa: BLE001 - not a valid mbox
            parsed_as_mbox = False
        if not parsed_as_mbox:  # single email file
            texts.append(_body_text(p.read_bytes()))
        if len(texts) >= MAX_NAZARIO:
            break
    texts = [t for t in texts if t.strip()][:MAX_NAZARIO]
    return pd.DataFrame({"text": texts, "label": "threat"})


def load_enron() -> pd.DataFrame:
    """Diverse real corporate email → `clean` (broadens the narrow SpamAssassin ham)."""
    if not ENRON_CSV.exists():
        return pd.DataFrame(columns=["text", "label"])
    df = pd.read_csv(ENRON_CSV)[["text"]].copy()
    df["label"] = "clean"
    return df


def main() -> None:
    parts = [load_spamassassin(), load_enron(), load_nazario()]
    df = pd.concat(parts, ignore_index=True)
    df = df[df["text"].astype(str).str.strip().str.len() > 0].drop_duplicates(subset=["text"])
    write_processed(df, "phase3_email")


if __name__ == "__main__":
    main()
