FROM python:3.12-slim

# zbar for QR decoding
RUN apt-get update && apt-get install -y --no-install-recommends libzbar0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
# Mount trained models at /models (or bake them in) and point the service at them.
ENV PHISHING_MODELS_DIR=/models
EXPOSE 8085

# Drop root: run the service as an unprivileged user.
RUN useradd --create-home --uid 10001 appuser
USER appuser

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8085"]
