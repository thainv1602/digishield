"""Core crawler primitives: polite HTTP, robots.txt, Vietnamese detection,
SMS normalization, dedup. Shared by every source adapter."""
from __future__ import annotations

import hashlib
import re
import time
import unicodedata
from dataclasses import dataclass, field
from urllib.parse import urlparse
from urllib.robotparser import RobotFileParser

import requests

UA = "DigiShield-Research/1.0 (+https://digishield.duckdns.org; anti-phishing dataset)"

# Unified label space, same as training (clean|spam|threat).
RAW_TO_UNIFIED = {
    "smishing": "threat", "phishing": "threat", "scam": "threat", "threat": "threat",
    "spam": "spam", "ads": "spam",
    "ham": "clean", "legit": "clean", "clean": "clean",
}


@dataclass
class Record:
    text: str
    raw_label: str                 # source-native label, mapped later
    source: str
    url: str = ""
    collected_at: str = ""         # ISO date, stamped by pipeline (Date.* unavailable here)
    meta: dict = field(default_factory=dict)

    @property
    def label(self) -> str:
        return RAW_TO_UNIFIED.get(self.raw_label.lower(), "threat")


# ---- Vietnamese detection (handles diacritics-stripped scam SMS too) ----

_VN_DIACRITICS = re.compile(
    r"[ăâđêôơưáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ]", re.I
)
# Common Vietnamese tokens that survive diacritic-stripping in scam texts.
_VN_WORDS = {
    "ban", "khong", "nhan", "ngan", "hang", "tai", "khoan", "trung", "thuong",
    "khach", "quy", "vui", "long", "lien", "he", "diem", "mien", "phi", "goi",
    "ngay", "de", "duoc", "cua", "gui", "tin", "nhap", "link", "truy", "cap",
    "vietcombank", "techcombank", "mbbank", "bidv", "vnpt", "viettel", "momo",
    "chuyen", "so", "du", "thanh", "toan", "otp", "ma", "xac", "minh", "canh", "bao",
}


def is_vietnamese(text: str) -> bool:
    if _VN_DIACRITICS.search(text):
        return True
    toks = set(re.findall(r"[a-z]+", text.lower()))
    return len(toks & _VN_WORDS) >= 2


# ---- Normalization & dedup ----

def normalize_sms(text: str) -> str:
    text = unicodedata.normalize("NFC", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def dedup_key(text: str) -> str:
    base = re.sub(r"\s+", "", text.lower())
    base = re.sub(r"\d+", "#", base)  # collapse phone numbers / amounts
    return hashlib.sha1(base.encode("utf-8")).hexdigest()


# ---- Polite HTTP fetcher (robots.txt aware + rate limited) ----

class HttpFetcher:
    def __init__(self, *, rate_delay: float = 2.0, timeout: int = 20, respect_robots: bool = True):
        self.rate_delay = rate_delay
        self.timeout = timeout
        self.respect_robots = respect_robots
        self._last: dict[str, float] = {}
        self._robots: dict[str, RobotFileParser] = {}
        self.session = requests.Session()
        self.session.headers["User-Agent"] = UA

    def _robots_ok(self, url: str) -> bool:
        if not self.respect_robots:
            return True
        p = urlparse(url)
        host = f"{p.scheme}://{p.netloc}"
        rp = self._robots.get(host)
        if rp is None:
            rp = RobotFileParser()
            rp.set_url(f"{host}/robots.txt")
            try:
                rp.read()
            except Exception:  # noqa: BLE001 - no robots => allow
                rp = None
            self._robots[host] = rp
        return True if rp is None else rp.can_fetch(UA, url)

    def _throttle(self, url: str) -> None:
        host = urlparse(url).netloc
        wait = self.rate_delay - (time.monotonic() - self._last.get(host, 0.0))
        if wait > 0:
            time.sleep(wait)
        self._last[host] = time.monotonic()

    def get(self, url: str) -> requests.Response | None:
        if not self._robots_ok(url):
            print(f"[crawl] robots.txt disallows: {url}")
            return None
        self._throttle(url)
        try:
            r = self.session.get(url, timeout=self.timeout)
            r.raise_for_status()
            return r
        except Exception as exc:  # noqa: BLE001
            print(f"[crawl] fetch failed {url}: {type(exc).__name__}: {exc}")
            return None
