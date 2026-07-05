# VN smishing crawl pipeline

Collects **Vietnamese phishing SMS** to close the training-data gap (see
`../RESEARCH_FINDINGS.md`). Pluggable sources → normalize → Vietnamese filter →
dedup → unified labels → `data/raw/sms/extra/vn_smishing_crawled.csv`, which
`training/phase2_sms/prepare.py` already folds into training.

## Run

```bash
cd research
python crawlers/run_crawl.py              # all enabled sources
python crawlers/run_crawl.py --dry-run    # collect + summarize, no write
python crawlers/run_crawl.py --only fixtures
```

Then refresh training data: `python training/phase2_sms/prepare.py`.

## Architecture

```
run_crawl.py ─► pipeline.run(config)
                  ├─ HttpFetcher   robots.txt + rate-limit + retries + UA
                  ├─ sources/*     pluggable adapters (REGISTRY in __init__.py)
                  ├─ is_vietnamese normalize / dedup (base.py)
                  └─ writes extra/vn_smishing_crawled.csv + .full.csv + .manifest.json
```

## Sources (`sources.json`)

| type | what it does | status |
|---|---|---|
| `fixtures` | seed of publicly-published bank/telco/NCSC scam SMS warnings | ✅ enabled |
| `internal_reports` | DigiShield's own "Report phishing" SMS export (CSV) | ✅ enabled (skips until you export) |
| `chongluadao` | mine VN message text from api.chongluadao.vn | ⚙️ off (mostly URLs; verify ToS) |
| `html_listing` | CSS-selector scrape of authorized warning pages | ⚙️ off (fill URLs+selectors) |
| `rss` | follow a security-news feed, extract quoted SMS | ⚙️ off (fill feed URL) |

Add a source by implementing `Source.collect() -> Iterable[Record]` in `sources/`
and registering it in `sources/__init__.py`.

## The two engines that actually scale VN data

1. **`internal_reports`** — the highest-value source. The backend
   `POST /reports/phishing` flow produces naturally-labelled Vietnamese SMS. Export
   SMS-channel reports to `data/raw/sms/reports_export.csv` (cols `content,channel,verdict`)
   and this pipeline turns them into training rows on every run.
2. **Live web sources** (`html_listing`/`rss`) — for telco/NCSC warning pages you are
   authorized to read. Fill real URLs + selectors; `robots.txt` is honored automatically.

## Labels

Source-native labels map to the unified space in `base.py:RAW_TO_UNIFIED`
(`smishing/phishing/scam → threat`, `ads → spam`, `ham/legit → clean`).

## ⚖️ Ethics & legal

- `robots.txt` is respected by default; `--no-robots` only for sources you own.
- Rate-limited (default 2s/host) with an identifying User-Agent.
- Only collect **publicly published** scam examples or **your own** user reports.
- Verify each source's ToS before using its content for **commercial training**
  (esp. ChongLuaDao/NCSC feeds) — see `../LICENSES.md`.
- The `fixtures` seed is hand-curated from public anti-scam warnings, not scraped.
