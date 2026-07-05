"""Shared training utilities: unified labels, device pick, and a generic
HuggingFace fine-tuning routine used by all three phases.

`prepare.py` scripts only need pandas (produce data/processed/*.csv).
`train.py` scripts additionally need transformers/datasets/torch.
"""
from __future__ import annotations

from pathlib import Path

import pandas as pd

ROOT = Path(__file__).resolve().parents[1]
PROCESSED = ROOT / "data" / "processed"
MODELS = ROOT / "models"

# Unified label space — matches backend ClassificationView (clean|spam|threat).
LABELS = ["clean", "spam", "threat"]


def pick_device() -> str:
    import torch

    if torch.cuda.is_available():
        return "cuda"
    if torch.backends.mps.is_available():
        return "mps"
    return "cpu"


def write_processed(df: pd.DataFrame, name: str) -> Path:
    """Persist a unified [text,label] dataframe to data/processed/<name>.csv."""
    PROCESSED.mkdir(parents=True, exist_ok=True)
    df = df.dropna(subset=["text", "label"]).copy()
    df["text"] = df["text"].astype(str).str.strip()
    df = df[df["text"].str.len() > 0]
    bad = set(df["label"].unique()) - set(LABELS)
    if bad:
        raise ValueError(f"unexpected labels {bad}; allowed {LABELS}")
    out = PROCESSED / f"{name}.csv"
    df[["text", "label"]].to_csv(out, index=False)
    dist = df["label"].value_counts().to_dict()
    print(f"[prepare] wrote {out.relative_to(ROOT)} — {len(df):,} rows, dist={dist}")
    return out


def train_classifier(
    csv_name: str,
    model_name: str,
    out_subdir: str,
    *,
    epochs: int = 3,
    max_len: int = 128,
    batch_size: int = 16,
    lr: float = 2e-5,
    test_size: float = 0.2,
    max_rows: int | None = None,
    seed: int = 42,
) -> Path:
    """Fine-tune `model_name` on data/processed/<csv_name>.csv and save to
    models/<out_subdir>/. Label set is derived from the data (2- or 3-class)."""
    import numpy as np
    from datasets import Dataset
    from sklearn.metrics import accuracy_score, f1_score, precision_recall_fscore_support
    from sklearn.model_selection import train_test_split
    from transformers import (
        AutoModelForSequenceClassification,
        AutoTokenizer,
        Trainer,
        TrainingArguments,
    )

    src = PROCESSED / f"{csv_name}.csv"
    if not src.exists():
        raise FileNotFoundError(f"{src} missing — run the phase prepare.py first")

    df = pd.read_csv(src).dropna(subset=["text", "label"])
    if max_rows and len(df) > max_rows:
        df = df.sample(n=max_rows, random_state=seed).reset_index(drop=True)

    labels = sorted(df["label"].unique())
    label2id = {l: i for i, l in enumerate(labels)}
    id2label = {i: l for l, i in label2id.items()}
    df["y"] = df["label"].map(label2id)
    print(f"[train] {csv_name}: {len(df):,} rows, classes={labels}, device={pick_device()}")

    tr, ev = train_test_split(
        df, test_size=test_size, random_state=seed, stratify=df["y"]
    )
    tok = AutoTokenizer.from_pretrained(model_name)

    def tokenize(batch):
        return tok(batch["text"], truncation=True, max_length=max_len, padding=False)

    ds_tr = Dataset.from_pandas(tr[["text", "y"]].rename(columns={"y": "labels"}), preserve_index=False).map(tokenize, batched=True)
    ds_ev = Dataset.from_pandas(ev[["text", "y"]].rename(columns={"y": "labels"}), preserve_index=False).map(tokenize, batched=True)

    model = AutoModelForSequenceClassification.from_pretrained(
        model_name, num_labels=len(labels), id2label=id2label, label2id=label2id
    )

    def metrics(pred):
        logits, y = pred
        p = np.argmax(logits, axis=1)
        pr, rc, f1, _ = precision_recall_fscore_support(y, p, average="macro", zero_division=0)
        return {"accuracy": accuracy_score(y, p), "f1_macro": f1, "precision": pr, "recall": rc}

    out_dir = MODELS / out_subdir
    args = TrainingArguments(
        output_dir=str(out_dir / "checkpoints"),
        num_train_epochs=epochs,
        per_device_train_batch_size=batch_size,
        per_device_eval_batch_size=batch_size * 2,
        learning_rate=lr,
        eval_strategy="epoch",
        save_strategy="epoch",
        load_best_model_at_end=True,
        metric_for_best_model="f1_macro",
        logging_steps=50,
        report_to=[],
        seed=seed,
    )
    from transformers import DataCollatorWithPadding

    trainer = Trainer(
        model=model,
        args=args,
        train_dataset=ds_tr,
        eval_dataset=ds_ev,
        compute_metrics=metrics,
        data_collator=DataCollatorWithPadding(tok),
    )
    trainer.train()
    print("[train] eval:", trainer.evaluate())
    out_dir.mkdir(parents=True, exist_ok=True)
    trainer.save_model(str(out_dir))
    tok.save_pretrained(str(out_dir))
    print(f"[train] saved model -> {out_dir.relative_to(ROOT)}")
    return out_dir
