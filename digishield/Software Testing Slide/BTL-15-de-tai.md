# Bài tập lớn — Kiểm thử Phần mềm (2025–2026)
## 15 đề tài trên nền codebase DigiShield

Mỗi nhóm **5 sinh viên** nhận **một đề tài**: xây **một chức năng mới** bám module có sẵn, viết **test đủ tầng đạt coverage > 90%**, giữ **pipeline CI xanh**.

- **Codebase:** Spring Modulith (Java 25, Spring Boot 4.1) + React/Vite. Đã có sẵn JaCoCo (aggregation) và GitHub Actions (`backend-ci.yml`, `frontend-ci.yml`, `commit-lint.yml`).
- **8 module nghiệp vụ** dùng cho đề: `reporting`, `interception`, `learning`, `simulation`, `analytics`, `tenancy`, `notification`, `auth`.

---

## A. Đề bài chung — 5 hạng mục nghiệm thu

Áp dụng cho **mọi** nhóm, bất kể đề tài. Cũng là khung chấm ở mục C.

1. **Chức năng mới** — 1–2 endpoint REST + logic service + thay đổi entity/repo, mở rộng đúng module được giao. Không sửa chức năng nhóm khác.
2. **Test đủ tầng** — unit `Mockito` (phủ nhánh logic service) · `@WebMvcTest` (controller) · `@DataJpaTest`/Testcontainers IT theo mẫu `*IT` sẵn có · Newman API test (buổi 9) · Selenium E2E nếu có UI (buổi 10–11).
3. **Coverage > 90%** — nâng `jacocoTestCoverageVerification` của module lên `minimum = 0.90` (giới hạn vào package của nhóm); `./gradlew :modules:<module>:check` phải xanh.
4. **Pipeline xanh** — mở PR vào `develop`; check **Backend CI** (test + check + jacoco + checkstyle) và **commit-lint** phải pass. Không merge khi CI đỏ.
5. **Báo cáo** — bảng đặc tả từng ca theo mẫu buổi 9 (mã ca · tiền điều kiện · bước · dữ liệu · kết quả mong đợi/thực tế) + ảnh HTML report + screenshot Selenium khi fail.

---

## B. 15 đề tài

Rải đều 8 module, tối đa 2–3 nhóm/module nhưng **khác package** ⇒ hạn chế xung đột.
Độ khó: **TB** = trung bình · **Khó** = thuần logic nhiều nhánh, hợp nhóm mạnh (vẫn dễ đạt > 90% nhờ phủ nhánh rõ ràng).

| # | Module | Đề tài | Khó | Test trọng tâm |
|---|--------|--------|-----|----------------|
| 01 | `reporting` | Chuẩn hoá & chống trùng blacklist theo `BlacklistType` (domain lowercase, phone E.164, URL canonical) | TB | phân hoạch tương đương · biên · `@WebMvcTest` |
| 02 | `reporting` | Auto-triage report lừa đảo (rule/keyword → `AiLabel` trước khi analyst duyệt) | TB | bảng quyết định · test rule từng nhánh |
| 03 | `interception` | Watchlist TTL/hết hạn — tự hết hạn `AccountWatchEntry`, `check` bỏ qua entry hết hạn | TB | test theo thời gian (Clock inject) · biên ngày |
| 04 | `interception` | Rules engine can thiệp v2 — ngưỡng cấu hình (tiền + watchlist-hit + tần suất) → `BLOCK/WARN/ALLOW` | **Khó** | bảng quyết định lớn · phủ nhánh/điều kiện cao |
| 05 | `learning` | Engine trao huy hiệu — trao `Badge` khi điểm vượt ngưỡng `PointRule`, kích hoạt bằng sự kiện | TB | event-driven · listener test · ngưỡng biên |
| 06 | `learning` | Chấm quiz + chính sách retry — tính điểm, pass/fail, số lần làm lại + cooldown | TB | phủ nhánh · biên điểm/lần thử |
| 07 | `learning` | Quy tắc cấp chứng chỉ — cấp `Certificate` khi hoàn thành khoá + đạt assessment | TB | máy trạng thái · tổ hợp điều kiện |
| 08 | `simulation` | Endpoint funnel campaign — tỷ lệ open/click/report/train trên `SimEvent` | TB | tổng hợp số liệu · biên chia 0 |
| 09 | `analytics` | Phát hiện người tái phạm — user click qua nhiều campaign → phát `RiskSignal` | TB | cross-entity · emit signal · listener |
| 10 | `analytics` | Risk-score suy giảm theo thời gian — điểm giảm dần nếu không có tín hiệu mới | TB | test theo thời gian · số học suy giảm |
| 11 | `tenancy` | Dịch vụ đánh giá feature-flag — resolve có kiểu, ưu tiên override tenant rồi default | TB | table-driven · tổ hợp override/default |
| 12 | `tenancy` | Đo dùng & chặn vượt quota — tăng `UsageMetering`, chặn khi vượt hạn mức gói → `403` | **Khó** | biên hạn mức · phân quyền · `@WebMvcTest` |
| 13 | `tenancy` | Proration khi đổi gói — tính tín dụng/phí khi `ChangePlanCommand` (nặng số học) | **Khó** | nhiều nhánh số học · biên ngày/tiền |
| 14 | `notification` | Gộp thông báo (digest) — gộp reminder mỗi user qua một gateway adapter mới | TB | ports-and-adapters · test adapter + service |
| 15 | `auth` | Vòng đời token reset mật khẩu — issue/expire/single-use trên nền provider đang stub | TB | máy trạng thái · biên thời gian · dùng-1-lần |

---

## C. Rubric chấm điểm (thang 100)

Ánh xạ trực tiếp 5 hạng mục ở mục A.

| Tiêu chí | Điểm | Đạt tối đa khi… |
|----------|:----:|-----------------|
| Chức năng mới chạy đúng | 25 | Build được, endpoint/logic đúng đặc tả, demo được luồng. |
| Test đủ tầng | 25 | Đủ unit + slice controller + IT/JPA + API (Newman); có ca **tiêu cực** & ca biên. |
| Coverage > 90% | 20 | JaCoCo > 90% trên package của nhóm; gate `0.90` xanh. |
| Pipeline xanh | 15 | PR qua Backend CI + commit-lint; không commit làm đỏ CI. |
| Báo cáo & đặc tả ca | 15 | Bảng đặc tả mẫu buổi 9 đầy đủ + HTML report + screenshot fail. |
| **Tổng** | **100** | Trừ điểm nếu checkstyle cảnh báo, commit không theo Conventional Commits, hoặc chạm code nhóm khác. |

---

## D. Cách bật gate 90% & giữ CI xanh

**Nâng ngưỡng coverage lên 90%** — thêm vào `modules/<module>/build.gradle.kts` (ghi đè rule mặc định 0.10):

```kotlin
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = "PACKAGE"
            includes = listOf("com.digishield.<module>.<feature>")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}
```

**Chạy & nộp:**

```bash
# test + cổng coverage 0.90 + checkstyle cho riêng module
./gradlew :modules:<module>:check

# report HTML của module
modules/<module>/build/reports/jacoco/test/html/index.html

# report gộp toàn dự án
./gradlew testCodeCoverageReport
```

**Quy trình:** branch riêng `btl/nhomNN-<slug>` → commit theo **Conventional Commits** (bị `commit-lint` kiểm) → mở PR vào `develop` → chờ **Backend CI** xanh → merge.

---

## E. Tránh xung đột giữa 15 nhóm

- Mỗi nhóm làm **một package feature riêng** trong module được giao — không sửa file chung; service gốc chỉ **thêm** method/endpoint, hạn chế đổi chữ ký cũ.
- Endpoint mới đặt **path riêng** (vd `/api/v1/blacklist/validate`) để 2 nhóm cùng module không đụng route.
- Migration DB (nếu có) đặt **số thứ tự Flyway riêng** theo nhóm; entity mới nằm trong package nhóm.
- Rebase thường xuyên từ `develop`; xử lý conflict trước khi mở PR.
- Lưu ý cặp dễ nhầm: `/blacklist` thuộc **reporting**, còn `/account-watchlist` thuộc **interception** — hai module khác nhau.

---

## F. Scaffold mẫu (đề #01)

Branch `btl/nhom01-blacklist-validation` — đã chạy thật `./gradlew :modules:reporting:check` = **BUILD SUCCESSFUL, gate 0.90 xanh** (LINE 94.9%):

```
modules/reporting/.../blacklistvalidation/
  BlacklistValueValidator.java       (logic thuần, @Component)
  ValidationResult.java              (record kết quả)
  BlacklistValidationController.java (POST /api/v1/blacklist/validate — path riêng)
  README_BTL_NHOM01.md
  test/.../BlacklistValueValidatorTest.java        (data-driven, đã pass)
  test/.../BlacklistValidationControllerTest.java  (status + body)
modules/reporting/build.gradle.kts   (+ cổng JaCoCo 0.90 scoped package)
```

Các nhóm khác nhân bản cấu trúc này cho module của mình.

---

*Bộ môn CNTT — GTVT Phân hiệu TP.HCM · Kiểm thử Phần mềm 2025–2026*
