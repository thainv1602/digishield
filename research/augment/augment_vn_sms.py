"""Augment the Vietnamese smishing training set with LLM-generated variants.

Uses Claude (Anthropic SDK, model claude-opus-4-8) to expand the small
public-warning seed into a larger, diverse set of realistic Vietnamese phishing/
spam/ham SMS — labelled for the phase-2 classifier. This is SYNTHETIC training
data for a defensive anti-phishing detector (DigiShield), not deployable content.

Output -> data/raw/sms/extra/vn_smishing_augmented.csv (+ .full.csv + manifest),
which training/phase2_sms/prepare.py already folds into training.

Auth: constructs a bare anthropic.Anthropic() — resolves ANTHROPIC_API_KEY or an
`ant auth login` profile automatically. Run --dry-run to preview without any API call.
"""
from __future__ import annotations

import argparse
import json
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "crawlers"))
import pandas as pd  # noqa: E402
from base import dedup_key, is_vietnamese, normalize_sms  # noqa: E402

RESEARCH_ROOT = Path(__file__).resolve().parents[1]
EXTRA_DIR = RESEARCH_ROOT / "data" / "raw" / "sms" / "extra"
MODEL = "claude-opus-4-8"

SYSTEM = (
    "You generate SYNTHETIC labelled Vietnamese SMS messages to train an anti-phishing "
    "SMS classifier for DigiShield, a security-awareness platform. The dataset teaches a "
    "model to DETECT scam/smishing texts and protect users. Produce realistic Vietnamese "
    "SMS in the requested category. Rules: write natural Vietnamese as real senders do — "
    "mix messages WITH and WITHOUT diacritics (Vietnamese scam SMS are often diacritic-"
    "stripped); vary wording, fake brand names, amounts, and fake phone numbers; for any "
    "link use an OBVIOUSLY FAKE placeholder domain (e.g. http://xac-minh-abc.top) — never a "
    "real domain or real working URL. Do not reuse the seed examples verbatim. Each message "
    "one to two sentences, SMS length."
)

# category key, unified label, description, a few seed flavours (diacritics-stripped, as real)
CATEGORIES = [
    ("bank", "threat", "gia mao ngan hang: khoa tai khoan, xac minh OTP, giao dich bat thuong",
     ["Tai khoan cua ban da bi khoa. Vui long truy cap [link] de xac minh trong 24h.",
      "VCB: Phat hien dang nhap la. Xac nhan tai [link] neu khong phai ban."]),
    ("prize", "threat", "trung thuong gia: xe, tien mat, qua tri an tu nha mang/thuong hieu",
     ["Chuc mung! So thue bao trung giai Nhat xe SH tu Viettel. Nhan tai [link]."]),
    ("evn", "threat", "gia mao dien luc EVN: hoa don qua han, doa cat dien",
     ["EVN: Hoa don tien dien qua han, thanh toan tai [link] de tranh bi cat dien."]),
    ("bhxh_tax", "threat", "gia mao BHXH / Tong cuc Thue: tro cap chua nhan, ma so thue sai",
     ["BHXH: Ban co khoan tro cap chua nhan, cap nhat tai [link] hom nay."]),
    ("delivery", "threat", "gia mao giao hang (GHN/GHTK/Shopee): sai dia chi, hoan tien don hang",
     ["Buu dien: Kien hang sai dia chi, cap nhat tai [link] de nhan hang."]),
    ("telco_sim", "threat", "gia mao nha mang: khoa SIM 2 chieu, chuan hoa thong tin thue bao",
     ["SIM se bi khoa 2 chieu do chua chuan hoa thong tin. Truy cap [link] ngay."]),
    ("loan_job", "threat", "moi vay tien lai 0% / tuyen CTV online luong cao lam tai nha",
     ["Ban duoc duyet vay 50 trieu lai 0%. Bam [link] dien thong tin de giai ngan."]),
    ("authority", "threat", "gia mao cong an/vien kiem sat/toa an: lien quan vu an, doa bat",
     ["Cong an: Ban lien quan vu an rua tien. Truy cap [link] va khai bao ngay."]),
    ("ewallet_gov", "threat", "gia mao vi dien tu (Momo/ZaloPay) / dinh danh dien tu VNeID",
     ["Momo: Tai khoan bi tam khoa, xac thuc lai tai [link] de mo khoa."]),
    ("spam", "spam", "quang cao rac hop phap: khuyen mai, sale, moi mua hang (khong lua dao)",
     ["Sale 70% toan bo cua hang duy nhat hom nay. Ghe ngay de san uu dai."]),
    ("clean", "clean", "tin nhan hop le binh thuong: nhac lich, nhan tin ca nhan, thong bao NH khong kem link",
     ["Anh oi cuoc hop chieu doi sang 3h30 phong A2 nhe.",
      "Vietcombank: TK 0123 +5.000.000d luc 14:32. So du 12.450.000d."]),
]


def build_request(label: str, desc: str, seeds: list[str], count: int, batch: int) -> dict:
    schema = {
        "type": "object",
        "properties": {
            "variants": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {"text": {"type": "string"}},
                    "required": ["text"],
                    "additionalProperties": False,
                },
            }
        },
        "required": ["variants"],
        "additionalProperties": False,
    }
    user = (
        f"Category: {desc}\n"
        f"Target label: {label}\n"
        f"Seed examples (style reference — do NOT copy):\n"
        + "\n".join(f"- {s}" for s in seeds)
        + f"\n\nGenerate {count} DISTINCT new Vietnamese SMS for this category. "
        f"Batch #{batch}: make this batch different from other batches — vary scenarios, "
        f"brands, and phrasing; alternate between diacritic and non-diacritic messages. "
        f"Return only the JSON object."
    )
    return {
        "model": MODEL,
        "max_tokens": 4000,
        "system": SYSTEM,
        "messages": [{"role": "user", "content": user}],
        "output_config": {"format": {"type": "json_schema", "schema": schema}, "effort": "low"},
    }


def generate(client, req: dict) -> list[str]:
    resp = client.messages.create(**req)
    if resp.stop_reason == "refusal":  # safety classifier declined — skip this batch
        print(f"[augment] refusal (skipped): {getattr(resp.stop_details, 'category', None)}")
        return []
    text = next((b.text for b in resp.content if b.type == "text"), "")
    try:
        return [v["text"] for v in json.loads(text).get("variants", []) if v.get("text")]
    except json.JSONDecodeError:
        print("[augment] could not parse model JSON, skipping batch")
        return []


def main() -> None:
    ap = argparse.ArgumentParser(description="LLM-augment Vietnamese smishing training data via Claude")
    ap.add_argument("--per-category", type=int, default=20, help="messages per category per round")
    ap.add_argument("--rounds", type=int, default=1, help="rounds per category (more = more variety)")
    ap.add_argument("--only", help="only this category key")
    ap.add_argument("--out", default="vn_smishing_augmented")
    ap.add_argument("--dry-run", action="store_true", help="assemble one request and print it; no API call")
    args = ap.parse_args()

    cats = [c for c in CATEGORIES if not args.only or c[0] == args.only]

    if args.dry_run:
        lab, desc, seeds = cats[0][1], cats[0][2], cats[0][3]
        req = build_request(lab, desc, seeds, args.per_category, 1)
        print(json.dumps(req, ensure_ascii=False, indent=2))
        print("\n[augment] DRY RUN — no API call made.")
        return

    try:
        import anthropic
    except ImportError:
        sys.exit("[augment] pip install anthropic")
    try:
        client = anthropic.Anthropic()
    except Exception as exc:  # noqa: BLE001
        sys.exit(f"[augment] client init failed: {exc}. Set ANTHROPIC_API_KEY or run `ant auth login`.")

    today = date.today().isoformat()
    rows, per_cat, seen = [], {}, set()
    for key, label, desc, seeds in cats:
        kept = 0
        for r in range(args.rounds):
            try:
                texts = generate(client, build_request(label, desc, seeds, args.per_category, r + 1))
            except Exception as exc:  # noqa: BLE001
                msg = str(exc).lower()
                if isinstance(exc, anthropic.AuthenticationError) or "authentication" in msg or "api_key" in msg:
                    sys.exit("[augment] no Anthropic credentials. Set ANTHROPIC_API_KEY or run "
                             "`ant auth login`, then re-run this script.")
                print(f"[augment] {key} round {r+1} failed: {type(exc).__name__}: {exc}")  # transient
                continue
            for t in texts:
                t = normalize_sms(t)
                if len(t) < 15 or not is_vietnamese(t):
                    continue
                dk = dedup_key(t)
                if dk in seen:
                    continue
                seen.add(dk)
                rows.append({"text": t, "label": label, "category": key,
                             "source": "claude-augment", "collected_at": today})
                kept += 1
        per_cat[key] = kept
        print(f"[augment] {key} ({label}): kept {kept}")

    if not rows:
        sys.exit("[augment] nothing generated (auth/refusal?) — see messages above.")

    df = pd.DataFrame(rows)
    EXTRA_DIR.mkdir(parents=True, exist_ok=True)
    df[["text", "label"]].to_csv(EXTRA_DIR / f"{args.out}.csv", index=False)
    df.to_csv(EXTRA_DIR / f"{args.out}.full.csv", index=False)
    summary = {"total": len(df), "model": MODEL, "per_category": per_cat,
               "label_dist": df["label"].value_counts().to_dict(), "date": today}
    (EXTRA_DIR / f"{args.out}.manifest.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"[augment] wrote {EXTRA_DIR / (args.out + '.csv')} — {len(df)} rows, dist={summary['label_dist']}")


if __name__ == "__main__":
    main()
