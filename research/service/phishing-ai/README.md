# DigiShield Phishing-AI service

FastAPI microservice serving the three fine-tuned phishing models. Mirrors the
backend `classify` contract (`label ∈ {clean,spam,threat}`, `confidence`, `reason`),
so a Java `MlAiClient` in `digishield/modules/ai` can call it over HTTP and reserve
Claude API for fallback (`escalate=true`) + explanation generation.

## Run locally

```bash
cd research/service/phishing-ai
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
# Point at trained models (defaults to research/models)
export PHISHING_MODELS_DIR=../../models
uvicorn app.main:app --reload --port 8085
```

The service boots even with no trained models — those modalities return
`escalate=true` so the caller falls back to Claude.

## Endpoints

| Method | Path | Body | Purpose |
|---|---|---|---|
| GET  | `/health` | — | status + which models are loaded |
| POST | `/classify` | `{"payload": "..."}` | auto-route email/sms/url |
| POST | `/classify/url` | `{"url": "..."}` | Phase 1 |
| POST | `/classify/sms` | `{"payload": "..."}` | Phase 2 |
| POST | `/classify/email` | `{"payload": "..."}` | Phase 3 |
| POST | `/classify/qr` | `{"image_base64": "..."}` | decode QR → classify URL |

```bash
curl -s localhost:8085/classify/url -H 'content-type: application/json' \
  -d '{"url":"http://paypa1-secure-login.tk/verify"}'
# {"label":"threat","confidence":0.99,"reason":"...","model":"phase1_url","escalate":false}
```

## Docker

```bash
docker build -t digishield/phishing-ai .
docker run -p 8085:8085 -v $(pwd)/../../models:/models digishield/phishing-ai
```

## Backend integration (next step, not yet wired)

Add a `MlAiClient implements AiClient` in `digishield/modules/ai` that POSTs to
`/classify`, maps the JSON to `ClassificationView`, and when `escalate=true`
delegates to the existing `ClaudeAiClient`. Gate with
`digishield.ai.ml.enabled=true` and `digishield.ai.ml.base-url=http://phishing-ai:8085`.
