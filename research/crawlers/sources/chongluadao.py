"""ChongLuaDao (chongluadao.vn) source.

The public API is primarily a URL/domain blocklist, but scam *reports* sometimes
carry a Vietnamese description/message body. This adapter probes the configured
API endpoints and extracts any free-text VN message field it finds. Best-effort:
if the API shape changes or returns only URLs, it yields nothing (not an error).

config:
  type: chongluadao
  endpoints: ["https://api.chongluadao.vn/v3/notes", ...]
  text_fields: ["message", "content", "description", "note"]   # fields to mine
  label: smishing
"""
from __future__ import annotations

from collections.abc import Iterable

from .base_source import Record, Source

DEFAULT_TEXT_FIELDS = ["message", "content", "description", "note", "body", "text"]


def _walk(obj):
    """Yield every dict found anywhere in a nested JSON structure."""
    if isinstance(obj, dict):
        yield obj
        for v in obj.values():
            yield from _walk(v)
    elif isinstance(obj, list):
        for v in obj:
            yield from _walk(v)


class ChongLuaDaoSource(Source):
    def collect(self) -> Iterable[Record]:
        endpoints = self.cfg.get("endpoints", [])
        fields = self.cfg.get("text_fields", DEFAULT_TEXT_FIELDS)
        label = self.cfg.get("label", "smishing")
        seen = 0
        for ep in endpoints:
            r = self.fetcher.get(ep)
            if r is None:
                continue
            try:
                data = r.json()
            except ValueError:
                print(f"[crawl] {self.name}: {ep} returned non-JSON, skipping")
                continue
            for node in _walk(data):
                for f in fields:
                    val = node.get(f)
                    if isinstance(val, str) and len(val.strip()) >= 15:
                        yield Record(text=val, raw_label=label, source=self.name, url=ep,
                                     meta={"field": f})
                        seen += 1
                        if seen >= self.max_items:
                            return
