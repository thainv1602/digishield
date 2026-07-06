"""Source adapter registry. Each source yields Record objects."""
from __future__ import annotations

from .chongluadao import ChongLuaDaoSource
from .fixtures import FixturesSource
from .html_listing import HtmlListingSource
from .internal_reports import InternalReportsSource
from .rss import RssSource

REGISTRY = {
    "chongluadao": ChongLuaDaoSource,
    "html_listing": HtmlListingSource,
    "rss": RssSource,
    "internal_reports": InternalReportsSource,
    "fixtures": FixturesSource,
}


def build(kind: str, cfg: dict, fetcher):
    if kind not in REGISTRY:
        raise ValueError(f"unknown source type '{kind}'. Known: {list(REGISTRY)}")
    return REGISTRY[kind](cfg, fetcher)
