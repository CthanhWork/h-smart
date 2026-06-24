# Bộ kiểm thử hiệu năng k6 cho H-Smart

Thư mục này chứa các kịch bản kiểm thử hiệu năng bằng `k6` cho hệ thống H-Smart. Mục tiêu là đo thời gian phản hồi, tỷ lệ lỗi, số lượng request xử lý được và khả năng chịu tải của `api-gateway` trên Google Cloud VPS và `ai-service` trên DigitalOcean VPS mà không phải sửa code backend, frontend hoặc AI-service.

## Những gì đã được phát hiện từ source code

- API Gateway chính: `api-gateway`
- AI-service riêng: `ai-service`
- Health check gateway: `/health`
- Đăng nhập lấy JWT: `/api/v1/auth/login`
- Danh mục sản phẩm: `/api/v1/products/categories`
- Danh sách sản phẩm: `/api/v1/products`
- Chi tiết sản phẩm: `/api/v1/products/{id}`
- Tìm kiếm sản phẩm: `/api/v1/search/products`
- Review theo người bán: `/api/v1/reviews/sellers/{sellerId}`
- Wishlist cần JWT: `/api/v1/products/wishlist`
- Lịch sử/conversation cần JWT: `/api/v1/interactions/conversations`
- AI nhận diện ảnh: `/api/v1/predict`
- Tên field upload ảnh của AI-service: `file`

Các endpoint trên được xác định từ:

- [api-gateway/src/main/resources/application.yml](/D:/H-smart/api-gateway/src/main/resources/application.yml:1)
- [product-service/src/main/java/com/hsmart/backend/presentation/controllers/ProductController.java](/D:/H-smart/product-service/src/main/java/com/hsmart/backend/presentation/controllers/ProductController.java:1)
- [review-service/src/main/java/com/hsmart/review/presentation/controllers/ReviewController.java](/D:/H-smart/review-service/src/main/java/com/hsmart/review/presentation/controllers/ReviewController.java:1)
- [interaction-service/src/main/java/com/hsmart/backend/presentation/controllers/ChatConversationController.java](/D:/H-smart/interaction-service/src/main/java/com/hsmart/backend/presentation/controllers/ChatConversationController.java:1)
- [user-service/src/main/java/com/hsmart/backend/presentation/controllers/AuthController.java](/D:/H-smart/user-service/src/main/java/com/hsmart/backend/presentation/controllers/AuthController.java:1)
- [ai-service/app/presentation/api/routes/predict.py](/D:/H-smart/ai-service/app/presentation/api/routes/predict.py:1)

## k6 dùng để làm gì trong project này

`k6` giúp mô phỏng nhiều người dùng ảo gửi request vào hệ thống để:

- kiểm tra hệ thống còn sống hay không
- đo độ trễ phản hồi của gateway và AI-service
- đánh giá tỷ lệ request lỗi khi tải tăng dần
- mô phỏng luồng duyệt sản phẩm gần với người dùng thật
- xuất kết quả JSON để chèn vào báo cáo đồ án

## Cài k6

Các lệnh dưới đây tham chiếu từ tài liệu chính thức của Grafana k6:

- Debian/Ubuntu:

```bash
curl -fsSL https://dl.k6.io/key.gpg | sudo gpg --dearmor -o /usr/share/keyrings/k6-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

- macOS:

```bash
brew install k6
```

- Windows:

```powershell
winget install k6 --source winget
```

## Kiểm tra version

```bash
k6 version
```

## Chuẩn bị trước khi chạy

Di chuyển vào đúng thư mục test:

```bash
cd performance-tests
```

Nếu chạy `ai-service-test.js`, hãy đặt ảnh test tên `sample.jpg` vào ngay trong thư mục `performance-tests/`. File này chưa được thêm sẵn để tránh commit nhầm dữ liệu không cần thiết.

## Biến môi trường hỗ trợ

- `BASE_URL`: URL của API Gateway hoặc backend chính
- `AI_BASE_URL`: URL của AI-service
- `TOKEN`: JWT token nếu API cần xác thực
- `HEALTH_PATH`: ghi đè endpoint health check nếu môi trường triển khai khác source code
- `PRODUCT_LIST_PATH`: ghi đè endpoint danh sách sản phẩm
- `PRODUCT_DETAIL_PATH`: ghi đè endpoint chi tiết sản phẩm, hỗ trợ dạng `/api/v1/products/{id}` hoặc `/api/v1/products/:id`
- `AI_DETECT_PATH`: ghi đè endpoint nhận diện ảnh của AI-service
- `AI_FILE_FIELD`: ghi đè tên field upload ảnh của AI-service

## Cách chạy từng file test

Smoke test:

```bash
k6 run -e BASE_URL=<API_GATEWAY_URL> smoke-test.js
```

Gateway load test:

```bash
k6 run -e BASE_URL=<API_GATEWAY_URL> gateway-load-test.js
```

Workflow test:

```bash
k6 run -e BASE_URL=<API_GATEWAY_URL> workflow-test.js
```

AI-service test:

```bash
k6 run -e AI_BASE_URL=<AI_SERVICE_URL> -e TOKEN=<JWT_TOKEN> ai-service-test.js
```

Stress test:

```bash
k6 run -e BASE_URL=<API_GATEWAY_URL> stress-test.js
```

Nếu cần truyền endpoint thủ công:

```bash
k6 run \
  -e BASE_URL=<API_GATEWAY_URL> \
  -e PRODUCT_LIST_PATH=<PRODUCT_LIST_ENDPOINT> \
  gateway-load-test.js
```

Với AI-service:

```bash
k6 run \
  -e AI_BASE_URL=<AI_SERVICE_URL> \
  -e TOKEN=<JWT_TOKEN> \
  -e AI_DETECT_PATH=<AI_DETECT_ENDPOINT> \
  -e AI_FILE_FIELD=<UPLOAD_FIELD_NAME> \
  ai-service-test.js
```

## Chạy trên Google Cloud VPS và DigitalOcean VPS

Nếu kiến trúc triển khai của bạn là:

- `api-gateway` public trên Google Cloud VPS
- `ai-service` public trên DigitalOcean VPS

thì thường sẽ chạy như sau:

- Test gateway trên GCP:

```bash
k6 run -e BASE_URL=<API_GATEWAY_URL> smoke-test.js
k6 run -e BASE_URL=<API_GATEWAY_URL> gateway-load-test.js
k6 run -e BASE_URL=<API_GATEWAY_URL> workflow-test.js
k6 run -e BASE_URL=<API_GATEWAY_URL> stress-test.js
```

- Test AI-service trên DO:

```bash
k6 run -e AI_BASE_URL=<AI_SERVICE_URL> -e TOKEN=<JWT_TOKEN> ai-service-test.js
```

Lưu ý:

- `BASE_URL` nên trỏ vào domain hoặc IP public của `api-gateway`
- `AI_BASE_URL` nên trỏ vào domain hoặc IP public của `ai-service`
- Nếu AI đi qua `api-gateway` và route bị bảo vệ bởi JWT, cần truyền thêm `TOKEN`
- cần mở firewall/VPC/ufw cho đúng cổng public của từng VPS trước khi chạy
- nếu `product-service` trong GCP đang gọi AI-service ở DO, `workflow-test.js` và `gateway-load-test.js` vẫn chỉ cần `BASE_URL` vì chúng đi qua gateway

## Kết quả JSON được lưu ở đâu

Mỗi script đều có `handleSummary()` để:

- in summary ra terminal
- ghi file JSON vào thư mục:

```text
performance-tests/results/
```

## Ý nghĩa các metric chính

- `http_req_duration`: thời gian phản hồi request
- `http_req_failed`: tỷ lệ request lỗi
- `checks`: tỷ lệ điều kiện kiểm tra thành công
- `http_reqs`: tổng số request đã gửi
- `iterations`: số vòng lặp test đã chạy
- `vus`: số virtual users đang hoạt động

## Cách đọc nhanh số liệu từ file JSON

- `metrics.http_reqs.count`: tổng số request
- `metrics.http_req_failed.rate`: tỷ lệ lỗi
- `metrics.http_req_duration.avg`: thời gian phản hồi trung bình
- `metrics.http_req_duration['p(95)']`: thời gian phản hồi p95
- `metrics.checks.rate`: tỷ lệ check thành công
- `metrics.iterations.count`: tổng số vòng lặp

## Ghi chú khi cần sửa thủ công

- Nếu môi trường thật không public `/health`, truyền lại `HEALTH_PATH`
- Nếu gateway public route khác source code, truyền lại `PRODUCT_LIST_PATH` và `PRODUCT_DETAIL_PATH`
- Nếu AI-service không dùng `/api/v1/predict`, truyền lại `AI_DETECT_PATH`
- Nếu AI-service không nhận field `file`, truyền lại `AI_FILE_FIELD`
- `AI_FILE_FIELD` hiện mặc định là `file` vì đây là field đã được phát hiện trong source FastAPI
- `workflow-test.js` và `gateway-load-test.js` chỉ dùng các request đọc dữ liệu an toàn; không gọi các API thay đổi trạng thái để tránh làm bẩn dữ liệu thật trên VPS
