# DigiShield — Phishing AI Research & Training

Tự huấn luyện các mô hình phát hiện phishing cho DigiShield thay vì phụ thuộc hoàn toàn
vào Claude API. Kết quả là một **service Python riêng** (FastAPI) phục vụ 3 mô hình,
khớp đúng contract `classify` của backend (`label ∈ {clean, spam, threat}`, `confidence`, `reason`).

Cơ sở nghiên cứu (dataset, benchmark, license — đã kiểm chứng 3/3 phiếu) xem
[`RESEARCH_FINDINGS.md`](./RESEARCH_FINDINGS.md).

## Cấu trúc

```
research/
├── scripts/            # Tải dataset về data/raw/
├── crawlers/           # Pipeline crawl SMS phishing tiếng Việt -> data/raw/sms/extra/
├── augment/            # Sinh biến thể SMS lừa đảo VN bằng Claude -> data/raw/sms/extra/
├── training/           # 3 phase fine-tune, mỗi phase: prepare.py + train.py
│   ├── phase1_url/     # DistilBERT trên URL (dùng cho QR/quishing + link)
│   ├── phase2_sms/     # PhoBERT trên SMS tiếng Việt (smishing)
│   └── phase3_email/   # DistilBERT/RoBERTa trên email
├── service/phishing-ai/# FastAPI microservice phục vụ 3 model
├── data/               # (gitignored) raw + processed datasets
└── models/             # (gitignored) checkpoint sau khi train
```

## Nhãn thống nhất (unified label space)

Mọi phase quy về 3 nhãn khớp backend `ClassificationView`:

| Nhãn      | Ý nghĩa                              | Map từ dataset gốc                    |
|-----------|--------------------------------------|---------------------------------------|
| `clean`   | Hợp lệ, an toàn                      | ham / legitimate / benign             |
| `spam`    | Rác, quảng cáo, phiền nhưng ít nguy  | spam                                  |
| `threat`  | Phishing/smishing/lừa đảo/độc hại    | phishing / smishing / malicious       |

Dataset chỉ có nhãn nhị phân (URL: phishing vs legit) map thẳng `threat` / `clean`.

## Chạy nhanh (quickstart)

```bash
cd research
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# 1) Tải toàn bộ dataset (chỉ nguồn license cho phép dùng thương mại)
python scripts/download_all.py

# 2) Phase 1 — URL/QR
python training/phase1_url/prepare.py
python training/phase1_url/train.py            # xuất ra models/phase1_url/

# 3) Phase 2 — SMS tiếng Việt
python training/phase2_sms/prepare.py
python training/phase2_sms/train.py            # xuất ra models/phase2_sms/

# 4) Phase 3 — Email
python training/phase3_email/prepare.py
python training/phase3_email/train.py          # xuất ra models/phase3_email/

# 5) Chạy service
cd service/phishing-ai
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8085
```

## License data — CHỈ dùng nguồn cho phép thương mại

Xem `LICENSES.md`. Tóm tắt: chỉ đóng gói vào model những dataset **CC BY 4.0 / public**
(UCI SMS, Mendeley SMS Phishing, LegitPhish, PhiUSIIL, SpamAssassin, ChongLuaDao feed,
Pham & Le-Hong VN SMS). **KHÔNG** đưa SmishTank (CC BY-NC-SA, phi thương mại) vào model ship.

## Nguồn nghiên cứu (đã kiểm chứng 3/3 phiếu)

- Email: IPSDM (Wiley spy2.402), MDPI Electronics 13/24/4877, MDPI Computation 14/2/46
- SMS VN: Pham & Le-Hong 2017 (arXiv:1705.04003); PhoBERT+FL (Springer LNCS 978-3-031-77731-8_24)
- QR/URL: CIC-Trap4Phish (arXiv:2602.09015); PhiUSIIL (UCI #967); LegitPhish (Data in Brief 2025)
- VN feed: github.com/elliotwutingfeng/ChongLuaDao-Phishing-Blocklist (api.chongluadao.vn, cập nhật hàng ngày)
