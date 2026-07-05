"""RSS/Atom source: read a security-news/telco feed, follow each article, and
extract quoted scam-SMS blocks via a CSS selector. Uses bs4 for both feed and
article parsing (no extra dependency).

config:
  type: rss
  label: smishing
  feeds: ["https://example-news.vn/rss/an-ninh-mang.xml"]
  article_selector: "blockquote, .sms-quote"
  keywords: ["tin nhắn", "mạo danh", "lừa đảo", "smishing"]   # article must match one
"""
from __future__ import annotations

from collections.abc import Iterable

from bs4 import BeautifulSoup

from .base_source import Record, Source


class RssSource(Source):
    def collect(self) -> Iterable[Record]:
        label = self.cfg.get("label", "smishing")
        selector = self.cfg.get("article_selector", "blockquote")
        keywords = [k.lower() for k in self.cfg.get("keywords", [])]
        count = 0
        for feed_url in self.cfg.get("feeds", []):
            r = self.fetcher.get(feed_url)
            if r is None:
                continue
            feed = BeautifulSoup(r.text, "xml")
            links = [i.find("link").get_text(strip=True) for i in feed.find_all("item") if i.find("link")]
            for link in links[: self.max_items]:
                art = self.fetcher.get(link)
                if art is None:
                    continue
                soup = BeautifulSoup(art.text, "lxml")
                page_text = soup.get_text(" ", strip=True).lower()
                if keywords and not any(k in page_text for k in keywords):
                    continue
                for node in soup.select(selector):
                    text = node.get_text(" ", strip=True)
                    if len(text) >= 20:
                        yield Record(text=text, raw_label=label, source=self.name, url=link)
                        count += 1
                        if count >= self.max_items:
                            return
