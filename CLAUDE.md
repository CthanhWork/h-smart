# H-Smart — Project context for Claude Code

Đây là file Claude Code tự động nạp khi chạy `claude` trong thư mục này. Nó nạp thêm tài liệu kiến trúc cũ và kế hoạch nâng cấp.

## Imports
- Kiến trúc & quy ước hiện có: @agent.md
- Kế hoạch nâng cấp trợ lý LLM (đang thực thi): @docs/LLM-ASSISTANT-UPGRADE-PLAN.md

## Bố cục repo
- Backend microservices (Spring Boot) nằm ngay trong repo này: `interaction-service`, `order-service`, `product-service`, `user-service`, `admin-service`, `api-gateway`, `ai-service`, `search-service`, `review-service`, `discovery-server`.
- Frontend (React + Vite) là **repo riêng** ở thư mục cạnh bên: `../H-smart UI`. Khi làm phần streaming (GĐ4) cần thêm thư mục này vào phiên: `claude --add-dir "../H-smart UI"` hoặc `/cd "../H-smart UI"`.

## Trọng tâm hiện tại: nâng cấp câu trả lời LLM
Toàn bộ scope, file sẽ chạm và tiêu chí nằm trong `docs/LLM-ASSISTANT-UPGRADE-PLAN.md`. Khi được yêu cầu "làm GĐ X", bám đúng phần giai đoạn đó trong tài liệu.

Code lõi của trợ lý nằm ở `interaction-service`:
- `service/impl/AssistantServiceImpl.java` — orchestration, dựng prompt.
- `infrastructure/ai/CloudAssistantClient.java` — gọi LLM `/chat/completions`.
- `infrastructure/config/AssistantProperties.java` + `src/main/resources/application.yml` — cấu hình `assistant.*`, `policy-search.*`.
- `infrastructure/policy/ElasticsearchPolicySearchClient.java` — truy hồi policy.

## Quy ước làm việc
- Mỗi giai đoạn làm trong một nhánh riêng; chạy `mvn -q -pl interaction-service test` (hoặc module liên quan) trước khi kết thúc.
- Thêm cấu hình mới qua biến môi trường (giữ giá trị mặc định an toàn), cập nhật `application.yml` và tài liệu env.
- Không commit secret.

## Tiến độ nâng cấp LLM
- [ ] GĐ0 — Baseline & bộ câu hỏi đo
- [x] GĐ1 — Tham số sinh (temperature/max_tokens/penalties)
- [x] GĐ2 — Mở rộng ngữ cảnh RAG + siết system prompt
- [x] GĐ3 — Hybrid/semantic search cho policy
- [x] GĐ4 — Streaming + chọn model theo tác vụ
- [ ] GĐ5 — Rollout & giám sát
