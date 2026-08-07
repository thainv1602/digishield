-- A starter training catalogue.
--
-- Until now the only writer was a @Profile("dev") seeder, so a real deployment
-- had an empty `course` table -- and an empty catalogue is not a cosmetic gap:
--
--   * SimulationClickListener logs "No course in the catalogue" and assigns
--     nothing, so clicking a simulation costs points and teaches nobody.
--   * remediationFor walks the catalogue's levels to send a repeat clicker
--     somewhere harder. With one course, or none, that ladder does nothing.
--   * With overdue suspension enabled, a learner can only clear a lock by
--     finishing training -- which has to exist first.
--
-- Three levels so the ladder has rungs. Each course carries lessons, so it can
-- be completed through progress alone; the quiz questions are what let someone
-- finish it the intended way.
--
-- Seeded for the demo tenant, which is the only one this deployment has and the
-- one the pre-token Lambda pins into every token. A second tenant would author
-- its own through the Content Studio rather than inherit these.
--
-- `checkpoints` is a comma-separated list: toLessonView splits on commas, so a
-- comma inside one checkpoint silently becomes two. Written to avoid them.
--
-- Idempotent: ON CONFLICT leaves anything already edited alone.

-- ── Level 1 ────────────────────────────────────────────────────────────────
INSERT INTO course (id, tenant_id, title, level, lang, duration_min, lesson_count, sort_order) VALUES
  ('c1000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111111',
   'Nhận biết email lừa đảo', 'BASIC', 'vi', 15, 2, 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO lesson (id, tenant_id, course_id, title, body, example_title, example_body, closing, checkpoints, duration_min, sort_order) VALUES
  ('11000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111111',
   'c1000000-0000-4000-8000-000000000001',
   'Bài 1 · Dấu hiệu của một email giả mạo',
   'Email lừa đảo hiếm khi hoàn hảo. Chúng thường tạo cảm giác gấp gáp ("tài khoản sẽ bị khoá trong 24 giờ"), dùng lời chào chung chung thay vì tên bạn, và dẫn tới một địa chỉ web gần giống nhưng không phải địa chỉ thật.',
   'Ví dụ thường gặp',
   'Một email xưng danh ngân hàng, gửi từ support@vietcornbank.com — thiếu một chữ cái so với tên thật — yêu cầu bạn "xác minh lại thông tin ngay".',
   'Khi thấy gấp gáp, hãy chậm lại. Đó chính là lúc kẻ tấn công cần bạn vội.',
   'Kiểm tra tên miền người gửi, Rê chuột xem đường dẫn thật trước khi bấm, Không ai có quyền yêu cầu mật khẩu của bạn',
   8, 1),
  ('11000000-0000-4000-8000-000000000002', '11111111-1111-1111-1111-111111111111',
   'c1000000-0000-4000-8000-000000000001',
   'Bài 2 · Làm gì khi nghi ngờ',
   'Không bấm, không trả lời, không chuyển tiếp cho đồng nghiệp. Hãy báo cáo email đó qua chức năng Báo cáo trong hệ thống — một email được báo sớm giúp bảo vệ tất cả những người còn lại chưa mở nó.',
   'Vì sao báo cáo quan trọng',
   'Một chiến dịch lừa đảo thường gửi tới hàng trăm người cùng lúc. Người báo cáo đầu tiên giúp đội an ninh chặn nó trước khi người thứ hai kịp bấm.',
   'Báo cáo không bao giờ là làm phiền. Báo nhầm còn hơn không báo.',
   'Không bấm vào liên kết đáng ngờ, Báo cáo ngay trong hệ thống, Lỡ bấm rồi thì đổi mật khẩu và báo ngay',
   7, 2)
ON CONFLICT (id) DO NOTHING;

INSERT INTO quiz_question (id, tenant_id, lesson_id, prompt, option_a, option_b, option_c, option_d, correct_index, explanation, sort_order) VALUES
  ('91000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111111',
   '11000000-0000-4000-8000-000000000001',
   'Dấu hiệu nào đáng ngờ nhất trong một email?',
   'Email có chữ ký và logo công ty',
   'Địa chỉ người gửi gần giống tên miền thật nhưng sai một ký tự',
   'Email được gửi vào buổi sáng',
   'Email có nhiều hơn một người nhận',
   1,
   'Tên miền gần giống là kỹ thuật phổ biến nhất: mắt người đọc lướt qua và tự động sửa lỗi chính tả giúp kẻ tấn công.',
   1),
  ('91000000-0000-4000-8000-000000000002', '11111111-1111-1111-1111-111111111111',
   '11000000-0000-4000-8000-000000000002',
   'Bạn nghi một email là lừa đảo. Việc nên làm đầu tiên?',
   'Chuyển tiếp cho đồng nghiệp để hỏi ý kiến',
   'Bấm vào liên kết để kiểm tra xem nó dẫn tới đâu',
   'Báo cáo email đó trong hệ thống và không bấm gì',
   'Xoá email và không nói với ai',
   2,
   'Chuyển tiếp làm lan rộng rủi ro, bấm thử là chính điều kẻ tấn công muốn, còn xoá im lặng khiến những người khác vẫn gặp nguy.',
   1)
ON CONFLICT (id) DO NOTHING;

-- ── Level 2 ────────────────────────────────────────────────────────────────
INSERT INTO course (id, tenant_id, title, level, lang, duration_min, lesson_count, sort_order) VALUES
  ('c1000000-0000-4000-8000-000000000002', '11111111-1111-1111-1111-111111111111',
   'Lừa đảo qua SMS và cuộc gọi', 'INTERMEDIATE', 'vi', 20, 1, 2)
ON CONFLICT (id) DO NOTHING;

INSERT INTO lesson (id, tenant_id, course_id, title, body, example_title, example_body, closing, checkpoints, duration_min, sort_order) VALUES
  ('12000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111111',
   'c1000000-0000-4000-8000-000000000002',
   'Bài 1 · SMS Brandname giả và cuộc gọi mạo danh',
   'Tin nhắn Brandname giả nguy hiểm hơn email vì nó chen được vào đúng luồng tin nhắn thật của ngân hàng trên điện thoại bạn. Cuộc gọi mạo danh cơ quan chức năng thì dựa vào sự sợ hãi: kẻ gọi tạo áp lực thời gian để bạn không kịp kiểm chứng.',
   'Ví dụ thường gặp',
   'Một tin nhắn nằm ngay dưới các tin thật của ngân hàng, báo tài khoản bị khoá và kèm một đường dẫn rút gọn.',
   'Không ai tử tế lại ép bạn quyết định trong ba phút. Hãy cúp máy và gọi lại số tổng đài in trên thẻ.',
   'Ngân hàng không bao giờ hỏi OTP, Cúp máy và gọi lại số chính thức, Đường dẫn rút gọn trong SMS là dấu hiệu xấu',
   20, 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO quiz_question (id, tenant_id, lesson_id, prompt, option_a, option_b, option_c, option_d, correct_index, explanation, sort_order) VALUES
  ('92000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111111',
   '12000000-0000-4000-8000-000000000001',
   'Người tự xưng nhân viên ngân hàng gọi và hỏi mã OTP vừa gửi. Bạn nên?',
   'Đọc mã vì họ gọi từ số tổng đài',
   'Từ chối, cúp máy và gọi lại số in trên thẻ ngân hàng',
   'Đọc mã nhưng dặn họ không dùng vào việc khác',
   'Nhắn mã qua tin nhắn cho an toàn hơn',
   1,
   'Không nhân viên ngân hàng nào cần OTP của bạn. Số gọi đến có thể bị giả mạo, nên cách kiểm chứng duy nhất là bạn chủ động gọi lại số chính thức.',
   1)
ON CONFLICT (id) DO NOTHING;

-- ── Level 3 ────────────────────────────────────────────────────────────────
INSERT INTO course (id, tenant_id, title, level, lang, duration_min, lesson_count, sort_order) VALUES
  ('c1000000-0000-4000-8000-000000000003', '11111111-1111-1111-1111-111111111111',
   'Tấn công có chủ đích và deepfake', 'ADVANCED', 'vi', 25, 1, 3)
ON CONFLICT (id) DO NOTHING;

INSERT INTO lesson (id, tenant_id, course_id, title, body, example_title, example_body, closing, checkpoints, duration_min, sort_order) VALUES
  ('13000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111111',
   'c1000000-0000-4000-8000-000000000003',
   'Bài 1 · Khi kẻ tấn công biết rõ về bạn',
   'Tấn công có chủ đích không gửi hàng loạt. Kẻ tấn công đọc trang công ty, mạng xã hội và cấu trúc phòng ban của bạn trước, rồi viết một email nhắc đúng dự án bạn đang làm. Deepfake giọng nói đưa việc này đi xa hơn: một cuộc gọi nghe đúng giọng cấp trên, yêu cầu chuyển khoản gấp.',
   'Ví dụ thường gặp',
   'Một email xưng danh giám đốc, gửi cuối giờ chiều thứ sáu, nhắc đúng tên khách hàng thật, yêu cầu thanh toán một hoá đơn "đã trễ hạn".',
   'Khi một yêu cầu bất thường đến kèm áp lực thời gian, hãy xác minh qua một kênh khác — gọi trực tiếp, hỏi mặt đối mặt.',
   'Xác minh qua kênh thứ hai, Yêu cầu tài chính gấp luôn cần kiểm chứng, Giọng nói không còn là bằng chứng nhận dạng',
   25, 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO quiz_question (id, tenant_id, lesson_id, prompt, option_a, option_b, option_c, option_d, correct_index, explanation, sort_order) VALUES
  ('93000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111111',
   '13000000-0000-4000-8000-000000000001',
   'Cấp trên gọi, đúng giọng, yêu cầu chuyển khoản gấp cho một đối tác. Bạn nên?',
   'Chuyển khoản vì nhận ra giọng nói',
   'Chuyển một phần trước cho an toàn',
   'Xác minh lại qua một kênh khác trước khi chuyển',
   'Hỏi lại mật khẩu để chắc chắn là cấp trên',
   2,
   'Giọng nói có thể được tạo lại từ vài giây ghi âm. Chỉ một kênh xác minh độc lập mới trả lời được câu hỏi "có đúng là người này không".',
   1)
ON CONFLICT (id) DO NOTHING;
