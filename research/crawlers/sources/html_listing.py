"""Config-driven HTML scraper for pages that publish scam-SMS examples verbatim
(telco warnings, NCSC alerts, news round-ups). You supply the page URLs and a CSS
selector for the quoted-message blocks; robots.txt is respected by the fetcher.

config:
  type: html_listing
  label: smishing
  pages:
    - url: "https://example-telco.vn/canh-bao-tin-nhan-gia-mao"
      selector: "blockquote.sms, .scam-sms-example"     # CSS selector for message text
  min_len: 20
"""
from __future__ import annotations

from collections.abc import Iterable

from bs4 import BeautifulSoup

from .base_source import Record, Source


class HtmlListingSource(Source):
    def collect(self) -> Iterable[Record]:
        label = self.cfg.get("label", "smishing")
        min_len = int(self.cfg.get("min_len", 20))
        count = 0
        for page in self.cfg.get("pages", []):
            url, selector = page["url"], page["selector"]
            r = self.fetcher.get(url)
            if r is None:
                continue
            soup = BeautifulSoup(r.text, "lxml")
            for node in soup.select(selector):
                text = node.get_text(" ", strip=True)
                if len(text) >= min_len:
                    yield Record(text=text, raw_label=label, source=self.name, url=url)
                    count += 1
                    if count >= self.max_items:
                        return
            if not soup.select(selector):
                print(f"[crawl] {self.name}: selector '{selector}' matched nothing on {url} "
                      "(page layout may have changed — update the selector)")
