# H-Smart Assistant — Knowledge base hướng dẫn người dùng

Đây là dữ liệu nguồn cho index `hsmart-policy-index` của `interaction-service`. Nội dung đã được đối chiếu với code ngày 22/06/2026.

Nguồn nạp máy đọc nằm tại:

- `interaction-service/src/main/resources/knowledge/hsmart-knowledge.ndjson`
- `scripts/load-assistant-knowledge.ps1`

Mỗi document có schema:

```json
{
  "title": "Tên chủ đề",
  "content": "Nội dung dùng để trả lời",
  "category": "Nhóm chủ đề",
  "version": 1,
  "updatedAt": "2026-06-22",
  "source": "hsmart-codebase"
}
```

## Nguyên tắc nội dung

- Chỉ mô tả chức năng đã có trong code.
- Không tự đặt chính sách đổi trả, hoàn tiền, thời gian giao hoặc cam kết thanh toán.
- Phân biệt hướng dẫn chung với dữ liệu riêng của người dùng.
- Trợ lý hiện chỉ truy hồi được đơn hàng gần nhất; chưa truy hồi trực tiếp trạng thái xác minh tài khoản hoặc một offer cụ thể.
- Khi `ASSISTANT_HYBRID_SEARCH_ENABLED=true`, index phải được bổ sung mapping và dữ liệu `embedding`. Bộ NDJSON hiện tại phục vụ BM25 mặc định.

## 16 chủ đề hiện có

### 1. Đăng ký tài khoản

Vào Đăng ký và nhập tên đăng nhập, email, mật khẩu. Họ tên, số điện thoại và địa chỉ là thông tin bổ sung. Sau khi đăng ký, người dùng phải xác minh email trước khi đăng nhập.

### 2. Xác minh email

Email xác minh có hiệu lực mặc định 24 giờ. Nếu liên kết hoặc mã hết hạn, người dùng có thể yêu cầu gửi lại. Trợ lý không được tự khẳng định tài khoản hiện tại đã xác minh hay chưa nếu không có dữ liệu tài khoản.

### 3. Đăng nhập

Đăng nhập bằng tên đăng nhập hoặc email cùng mật khẩu. Tài khoản chưa xác minh email hoặc đang bị khóa không thể đăng nhập.

### 4. Quên mật khẩu

Yêu cầu đặt lại mật khẩu bằng email. Token đặt lại có hiệu lực mặc định 30 phút. Sau khi đặt mật khẩu mới, các refresh token cũ của tài khoản bị thu hồi.

### 5. Đổi mật khẩu, email và hồ sơ

Người dùng đã đăng nhập có thể đổi mật khẩu, cập nhật hồ sơ và địa chỉ. Đổi email cần xác nhận email mới; mã/token xác nhận có hiệu lực mặc định 30 phút.

### 6. Cách đăng bán

Người bán phải tải ít nhất một ảnh, điền tên, mô tả, giá và danh mục. AI có thể gợi ý tên và giá; nếu AI không nhận diện được, người bán tự nhập tên. Tin mới luôn ở trạng thái `PENDING_REVIEW`.

### 7. Vì sao tin chờ duyệt

Tin mới chỉ hiển thị công khai sau khi quản trị viên duyệt thành `APPROVED`. Không khẳng định việc sửa tiêu đề tự động đưa tin về chờ duyệt vì code hiện tại chưa thực hiện hành vi đó.

### 8. Sửa, ẩn và xóa tin

Người bán có thể sửa nội dung hoặc ảnh của tin mình sở hữu, chuyển tin sang `HIDDEN`, hoặc xóa mềm tin. Người bán không thể tự đặt trạng thái `APPROVED`, `PENDING_REVIEW` hay `SOLD`.

### 9. Giá gợi ý

Giá AI chỉ là mức tham khảo dựa trên giá trung bình của sản phẩm tương tự. Người bán vẫn tự quyết định giá đăng.

### 10. Cách mua và đặt hàng

Chỉ sản phẩm `APPROVED` mới được đặt hàng. Người mua có thể xem phí vận chuyển dự kiến rồi mua trực tiếp hoặc thanh toán theo offer đã được chấp nhận. Đơn mới bắt đầu ở `PENDING`.

### 11. Trả giá

Mức giảm tối đa là 30%. Một buyer không thể có nhiều offer đang hoạt động cho cùng sản phẩm. Offer tồn tại 24 giờ; sau khi gửi hoặc gửi lại, cooldown mặc định là 1 giờ. Offer đã được chấp nhận phải dùng khi checkout mới tạo giá đơn giảm.

### 12. Theo dõi và vận chuyển

H-Smart hỗ trợ `GHTK` và `VIETTEL_POST`; mặc định hiện tại là Viettel Post. Phí chỉ là ước tính cho đến khi nhà vận chuyển xử lý. Không tự hứa thời gian giao nếu context không có ETA.

### 13. Hủy đơn

Buyer hoặc seller có thể hủy đơn `PENDING`. Chỉ seller có thể hủy đơn `PROCESSING`, và có thể cần hủy vận đơn thủ công với nhà vận chuyển. Đơn `PENDING` quá 30 phút mặc định sẽ tự chuyển `CANCELLED`.

### 14. Đánh giá người bán

Chỉ buyer của đơn `COMPLETED` mới có thể tạo đánh giá cho giao dịch đó. Mỗi đơn chỉ được đánh giá theo các ràng buộc của review-service; đánh giá bị quản trị viên ẩn sẽ không xuất hiện công khai.

### 15. Sản phẩm yêu thích

Người dùng có thể thả tim để lưu sản phẩm. Tin không còn hiển thị công khai, đã ẩn hoặc đã bán có thể không xuất hiện trong danh sách công khai.

### 16. Chat với người bán

H-Smart hỗ trợ chat thời gian thực giữa người mua và người bán theo sản phẩm. Chat dùng để hỏi tình trạng món đồ và trao đổi; không thay thế trạng thái offer, đơn hàng hoặc vận chuyển trong hệ thống.

## Nạp dữ liệu

```powershell
$env:ELASTICSEARCH_URL = "http://localhost:9200"
.\scripts\load-assistant-knowledge.ps1
```

Script dùng `_id` cố định nên có thể chạy lại mà không tạo document trùng. Sau khi nạp, nên kiểm tra:

```powershell
Invoke-RestMethod "$env:ELASTICSEARCH_URL/hsmart-policy-index/_count"
```
