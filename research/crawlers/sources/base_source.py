"""Source ABC — a named collector that yields Record objects."""
from __future__ import annotations

from abc import ABC, abstractmethod
from collections.abc import Iterable

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from base import HttpFetcher, Record  # noqa: E402


class Source(ABC):
    def __init__(self, cfg: dict, fetcher: HttpFetcher):
        self.cfg = cfg
        self.name = cfg.get("name", self.__class__.__name__)
        self.fetcher = fetcher
        self.max_items = int(cfg.get("max_items", 500))

    @abstractmethod
    def collect(self) -> Iterable[Record]:
        ...
