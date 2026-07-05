    # Dataset licenses — commercial / multi-tenant usability

DigiShield is a commercial multi-tenant product, so only datasets that permit
commercial redistribution of a **derived model** are bundled into the shipped models.

| Dataset | License | Commercial model OK? | Used in |
|---|---|---|---|
| UCI SMS Spam Collection | CC BY 4.0 | ✅ Yes (attribution) | Phase 2 |
| Mendeley SMS Phishing (f45bkkt8pr) | CC BY 4.0 | ✅ Yes (attribution) | Phase 2 |
| Pham & Le-Hong VN SMS (pth1993) | Research release | ⚠️ Research; verify before ship | Phase 2 |
| LegitPhish (Mendeley hx4m73v2sf) | CC BY 4.0 | ✅ Yes (attribution) | Phase 1 |
| PhiUSIIL (UCI #967) | CC BY 4.0 | ✅ Yes (attribution) | Phase 1 |
| SpamAssassin Public Corpus | Public / Apache | ✅ Yes | Phase 3 |
| Nazario Phishing Corpus | Research | ⚠️ Research; verify before ship | Phase 3 |
| Enron email | Public | ✅ Yes | Phase 3 |
| ChongLuaDao blocklist | See repo (aggregates api.chongluadao.vn) | ⚠️ Feed — verify ToS for training use | Phase 1 (VN augment) |
| **SmishTank** | **CC BY-NC-SA 4.0** | ❌ **NO — non-commercial** | eval only, NOT shipped |

## Attribution required in shipped model card

- UCI SMS Spam Collection — Almeida & Gómez Hidalgo, UCI ML Repository
- Mendeley SMS Phishing — Mishra & Soni, 2022
- Pham & Le-Hong, 2017, "Content-based approach for Vietnamese spam SMS filtering", arXiv:1705.04003
- LegitPhish — Data in Brief 2025 (Mendeley hx4m73v2sf)
- PhiUSIIL — UCI ML Repository #967, 2024
- ChongLuaDao (Chống Lừa Đảo) project — api.chongluadao.vn

> ⚠️ **Action item chưa xong**: xác nhận ToS của ChongLuaDao/NCSC feed cho mục đích
> huấn luyện thương mại. Feed dùng làm nguồn URL VN; nếu ToS hạn chế, chỉ dùng cho
> blocklist runtime (branch `feat/chongluadao-blocklist`), không đưa vào training set.
