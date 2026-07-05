# Phishing-AI research findings (verified)

Deep-research run: 27 sources fetched → 117 claims → 25 verified with 3-vote
adversarial checks → **25/25 confirmed, 0 refuted**. Summary of what to train on.

## Recommendation

Fine-tune **small open transformers per modality**, blend large English public
corpora + scarce Vietnamese data, and handle QR by **decode → classify URL**
(not image CNN). Self-trained model = primary classifier (fast/cheap/offline);
**Claude API = fallback for low-confidence cases + explanation generation**.

| Modality | Model | Reported benchmark |
|---|---|---|
| Email | RoBERTa / DistilBERT | ~99% acc (IPSDM; MDPI Electronics 13/24/4877) |
| SMS (VN) | **PhoBERT** | 99.38% acc FedAvg (Springer LNCS 978-3-031-77731-8_24) |
| SMS (multilingual) | XLM-RoBERTa / mBERT | — |
| QR (quishing) | decode → ModernBERT/DeBERTa on URL | 99.3% F1 vs 88% image-CNN (CIC-Trap4Phish, arXiv 2602.09015) |

## Datasets (commercial-friendly = shippable)

### Email — Phase 3
- **SpamAssassin** 6,047 (~31% spam), EN, public — ✅ auto-downloaded (ham+spam)
- **Nazario** 4,555 phishing-only EML — research; manual (academictorrents…9197995a)
- **Enron** legit email — public; balance the phishing class

### SMS — Phase 2
- **UCI SMS Spam** 5,574 (4,827 ham + 747 spam), EN, CC BY 4.0 — ✅ downloaded
- **Mendeley SMS Phishing** (f45bkkt8pr) 5,971 (~19% malicious), CC BY 4.0 — has a
  distinct *smishing* label → the missing `threat` class. Manual (Mendeley 403 on scripted GET)
- **Pham & Le-Hong VN SMS** 6,599 msgs — research; ⚠️ repo ships only a 10-line sample
- ❌ **SmishTank** 1,090 — CC BY-NC-SA (non-commercial), eval only

### URL / QR — Phase 1
- **PhiUSIIL** (UCI #967) 235,795 URLs (134,850 legit / 100,945 phishing), CC BY 4.0 — ✅ downloaded
- **LegitPhish** (Mendeley hx4m73v2sf) 101,219 URLs, CC BY 4.0 — manual
- **CIC-Trap4Phish** ~1.005M QR image subset — for the image-CNN comparison path
- **ChongLuaDao feed** 34,467 VN phishing URLs, daily — ✅ downloaded (VN threat signal)
- PhishTank / OpenPhish / URLhaus — live feeds to refresh phishing URLs

## Vietnamese data gap (biggest weakness)

Only one substantive public VN SMS corpus (2017, and only a sample is redistributed);
**no** public VN phishing-email or VN quishing dataset surfaced. Close the gap by:
1. **ChongLuaDao / NCSC-VNCERT** feeds (URLs) — already wired for Phase 1
2. **DigiShield user "Report phishing"** flow → naturally-labelled VN data over time
3. **Augmentation** — back-translation (EN→VI) + LLM-generated VN smishing (Claude)

## Caveats
- ~99% numbers are self-reported, in-distribution — real multilingual/adversarial
  performance will be lower. Several quishing sources are 2026 non-peer-reviewed preprints.
- License is a live constraint: keep SmishTank out of shipped models; verify
  ChongLuaDao/NCSC ToS before using the feed for *training* (fine for blocklist runtime).

## Open questions
1. ChongLuaDao/NCSC feed ToS for commercial *training* use?
2. Do near-99% benchmarks hold on code-switched VI-EN and adversarial obfuscation?
3. Accuracy of decoded-URL classifiers on VN-hosted + shortened/redirect URLs?
