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
    def _records_from_node(self, node, fields, label, ep) -> Iterable[Record]:
        """Yield a Record for each configured text field present on a JSON node."""
        for f in fields:
            val = node.get(f)
            if isinstance(val, str) and len(val.strip()) >= 15:
                yield Record(text=val, raw_label=label, source=self.name, url=ep, meta={"field": f})

    def _records_from_endpoint(self, ep, fields, label) -> Iterable[Record]:
        """Fetch one endpoint and mine every JSON node for text fields."""
        r = self.fetcher.get(ep)
        if r is None:
            return
        try:
            data = r.json()
        except ValueError:
            print(f"[crawl] {self.name}: {ep} returned non-JSON, skipping")
            return
        for node in _walk(data):
            yield from self._records_from_node(node, fields, label, ep)

    def collect(self) -> Iterable[Record]:
        endpoints = self.cfg.get("endpoints", [])
        fields = self.cfg.get("text_fields", DEFAULT_TEXT_FIELDS)
        label = self.cfg.get("label", "smishing")
        seen = 0
        for ep in endpoints:
            for rec in self._records_from_endpoint(ep, fields, label):
                yield rec
                seen += 1
                if seen >= self.max_items:
                    return
