"""Shared download utilities for research dataset fetchers."""
from __future__ import annotations

import io
import sys
import zipfile
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "data" / "raw"
PROCESSED = ROOT / "data" / "processed"

UA = {"User-Agent": "DigiShield-Research/1.0 (+https://digishield.duckdns.org)"}
TIMEOUT = 60


def ensure_dirs() -> None:
    RAW.mkdir(parents=True, exist_ok=True)
    PROCESSED.mkdir(parents=True, exist_ok=True)


def log(msg: str) -> None:
    print(f"[download] {msg}", flush=True)


def fetch(url: str, dest: Path, *, force: bool = False) -> Path:
    """Download `url` to `dest` (skips if present unless force)."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and not force:
        log(f"skip (exists): {dest.relative_to(ROOT)}")
        return dest
    log(f"GET {url}")
    with requests.get(url, headers=UA, timeout=TIMEOUT, stream=True) as r:
        r.raise_for_status()
        with open(dest, "wb") as f:
            for chunk in r.iter_content(chunk_size=1 << 16):
                f.write(chunk)
    log(f"saved -> {dest.relative_to(ROOT)} ({dest.stat().st_size:,} bytes)")
    return dest


def fetch_zip(url: str, dest_dir: Path, *, force: bool = False) -> Path:
    """Download a zip and extract into dest_dir."""
    dest_dir.mkdir(parents=True, exist_ok=True)
    marker = dest_dir / ".extracted"
    if marker.exists() and not force:
        log(f"skip (extracted): {dest_dir.relative_to(ROOT)}")
        return dest_dir
    log(f"GET (zip) {url}")
    r = requests.get(url, headers=UA, timeout=TIMEOUT)
    r.raise_for_status()
    with zipfile.ZipFile(io.BytesIO(r.content)) as z:
        z.extractall(dest_dir)
    marker.write_text("ok")
    log(f"extracted -> {dest_dir.relative_to(ROOT)}")
    return dest_dir


def try_step(name: str, fn) -> bool:
    """Run a download step, catching network/HTTP errors so one failure
    does not abort the whole batch. Returns True on success."""
    try:
        fn()
        return True
    except Exception as exc:  # noqa: BLE001 - report and continue
        log(f"!! FAILED {name}: {type(exc).__name__}: {exc}")
        return False
