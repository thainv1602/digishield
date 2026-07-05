# LLM augmentation — Vietnamese smishing

Expands the small public-warning seed (`../crawlers/fixtures/vn_smishing_seed.txt`)
into a larger, diverse **synthetic** Vietnamese SMS set — labelled `threat`/`spam`/`clean`
— to grow the phase-2 `threat` class fast while real VN data is scarce. Output lands in
`../data/raw/sms/extra/`, which `training/phase2_sms/prepare.py` already folds in.

> Synthetic data is a supplement, not a replacement. Keep a real held-out set
> (crawled / user-reported) for evaluation so you don't just measure the generator.

## Model & approach

- **Anthropic SDK**, model **`claude-opus-4-8`**, **structured outputs** (JSON schema)
  so the reply is a clean list — no brittle parsing.
- Refusals are handled: a `stop_reason == "refusal"` batch is skipped, not fatal.
- Diversity without `temperature` (removed on Opus 4.8): per-round batch nonce +
  rotating seeds + explicit "make this batch different" instruction.
- Every generated line is re-run through the crawler's Vietnamese filter + dedup
  before it's written, so English/duplicate outputs are dropped.

## Run

```bash
cd research
# auth: export ANTHROPIC_API_KEY=...   (or `ant auth login`)
python augment/augment_vn_sms.py --per-category 25 --rounds 2
python training/phase2_sms/prepare.py     # fold the new rows into training
```

Flags: `--per-category N`, `--rounds R` (more variety), `--only <category>`,
`--out <name>`, `--dry-run` (print one assembled request, **no API call**).

Categories cover the common VN scam families (fake bank/OTP, prize, EVN electricity,
BHXH/tax, delivery, telco SIM, loan/job, police/court, e-wallet/VNeID) plus `spam` and `clean`.

## Safety / ethics

Generated messages are synthetic training data for a **defensive** anti-phishing
classifier. The prompt forbids real domains/working links (fake placeholder domains only)
and real phone numbers. Do not repurpose the output as actual messages.
