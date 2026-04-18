# H-Smart Microservices Architecture

## Muc tieu

Tai lieu nay ghi chu kien truc microservices du kien cho `H-Smart`, theo huong:

- tach ro AI, nghiep vu san pham, nguoi dung, va tuong tac
- de mo rong dan ma khong phai dap bo monolith
- giu cho MVP van thuc dung, khong qua phuc tap som

## Nguyen tac kien truc

1. Moi service so huu database rieng.
2. Khong service nao truy cap truc tiep database cua service khac.
3. Service giao tiep voi nhau qua HTTP API hoac event, khong join cheo DB.
4. `api-gateway` dung de route, CORS, rate limiting, va xac thuc JWT co ban.
5. Authorization nghiep vu van phai duoc kiem tra trong tung service.

## Danh sach service

### 1. `api-gateway`

Vai tro:

- la cua ngo duy nhat cho Frontend
- che giau kien truc mang noi bo
- route request toi dung service

Trach nhiem:

- Routing:
  - `/api/users/**` -> `user-service`
  - `/api/products/**` -> `product-service`
  - `/api/interactions/**` -> `interaction-service`
  - `/uploads/**` hoac `/media/**` -> `product-service`
- JWT validation o muc co ban
- rate limiting
- CORS tap trung
- header forwarding (`userId`, `role`, correlation id neu can)

Khong nen chua:

- business logic
- database
- authorization chi tiet cho tung nghiep vu

Cong nghe de xuat:

- Spring Cloud Gateway

Luu y trien khai:

- Frontend chi nen biet host cua `api-gateway`
- Neu anh san pham dang duoc luu va phuc vu boi `product-service`, gateway phai co route proxy cho anh
- Khong nen de Frontend goi thang `product-service:8080` de lay anh, vi nhu vay se pha vo vai tro "cua ngo duy nhat"

### 2. `user-service`

Vai tro:

- quan ly danh tinh va ho so nguoi dung

Pham vi:

- dang ky
- dang nhap
- bcrypt password
- JWT issue / refresh strategy
- profile:
  - ho ten
  - so dien thoai
  - avatar
  - dia chi giao dich
- trust / verification:
  - OTP
  - diem uy tin
  - basic verification

Database:

- `hsmart_user_db` (PostgreSQL)

Cong nghe:

- Spring Boot 3
- Java 17
- Spring Security

### 3. `product-service`

Vai tro:

- service trung tam cho catalog va listing

Pham vi:

- categories / catalog
- CRUD tin dang
- gia, tinh trang, mo ta
- media upload
- static image URL
- AI integration
- smart naming
- search / filter
- luu `ai_metadata`

Chi tiet nghiep vu:

- nhan `MultipartFile` tu Frontend
- luu file vao `/uploads/`
- goi sang `ai-service`
- nhan ket qua detection
- tu sinh ten san pham neu form bo trong
- luu metadata AI vao `jsonb`
- sinh ra media URL theo host cua `api-gateway`, khong phai URL noi bo cua service

Tim kiem:

- filter theo gia
- filter theo danh muc
- filter theo AI label
- phan trang
- sap xep

Database:

- `hsmart_product_db` (PostgreSQL)

Bang chinh:

- `products`
- `categories`

Cot quan trong:

- `ai_metadata jsonb`

Cong nghe:

- Spring Boot 3
- Java 17
- Spring Data JPA

### 4. `ai-service`

Vai tro:

- suy luan AI va trich xuat vat the trong anh

Pham vi:

- load model Detectron2 da train tren LVIS
- nhan anh tu `product-service`
- tra ve JSON contract chuan:
  - `num_detections`
  - `label`
  - `score`
  - `bbox`
  - `class_id`

Khong can:

- database

Artifacts can doc:

- `model.pth`
- `config_infer.yaml`
- `classes.json`

Cong nghe:

- Python 3.10
- FastAPI
- PyTorch CPU-only
- Docker

### 5. `interaction-service`

Vai tro:

- xu ly cac tuong tac giua nguoi mua va nguoi ban

Pham vi:

- chat
- notification
- review / rating

Khuyen nghi ranh gioi:

- MVP co the gop chung trong 1 service
- nhung ve mat nghiep vu:
  - `chat + notification` phu hop hon voi huong realtime/document
  - `review` mang tinh relation cao hon document
  - `review` nen can nhac dua ve `user-service` neu phuc vu trust score
  - hoac dua ve `product-service` neu review gan chat voi bai dang / giao dich san pham

Neu van gop chung:

- can luu y chon database cho phu hop tung loai du lieu

Database de xuat:

- `hsmart_interaction_db`
- MongoDB la hop ly cho:
  - chat message
  - notification stream

Khuyen nghi thuc dung:

- O giai doan nay, `interaction-service` nen uu tien `chat + notification`
- Khong nen khoa chet `review` vao MongoDB neu sau nay can:
  - tinh diem trung binh
  - thong ke theo user / seller
  - rang buoc review voi giao dich cu the

Cong nghe:

- Spring Boot 3
- Java 17
- WebSocket

## So do luong giao tiep

### Dang nhap

`Frontend -> api-gateway -> user-service`

### Tao tin dang

`Frontend -> api-gateway -> product-service -> ai-service`

### Lay anh san pham

`Frontend -> api-gateway -> product-service (/uploads/** hoac /media/**)`

### Xem san pham

`Frontend -> api-gateway -> product-service`

### Chat

`Frontend -> api-gateway -> interaction-service`

### Xem profile nguoi ban

Hai huong co the dung:

1. `Frontend -> api-gateway -> user-service`
2. `Frontend -> api-gateway -> product-service`, sau do `product-service` goi `user-service` neu can profile toi gian

## Data ownership

### `user-service`

So huu:

- account
- password hash
- role
- profile
- trust score

### `product-service`

So huu:

- product
- category
- image path / media metadata
- AI metadata
- listing status

### `interaction-service`

So huu:

- conversations
- messages
- notifications
- reviews neu giu trong service nay

## Dieu can giu chat

### 1. Gateway khong duoc la noi duy nhat kiem tra quyen

Gateway co the:

- check JWT hop le
- forward claims

Nhung moi service van phai tu kiem tra:

- user co phai chu bai dang khong
- user co nam trong conversation khong
- user co quyen admin hay khong

### 2. Khong truy cap cheo DB

Vi du:

- `product-service` khong duoc query truc tiep `hsmart_user_db`
- `interaction-service` khong duoc doc truc tiep `hsmart_product_db`

### 3. Upload local chi la giai phap MVP

O giai doan dau:

- luu local volume `/uploads/` la du

Ve sau:

- nen nang cap sang MinIO / S3-compatible storage

### 4. Search nen de trong `product-service` o giai doan dau

Hien tai giu search trong `product-service` la hop ly.

Neu ve sau co nhu cau:

- full-text search manh
- ranking
- search by relevance
- analytics search

thi tach sang search layer rieng hoac Elasticsearch/OpenSearch.

### 5. Event-driven la huong mo rong sau

Ban dau co the dung REST cho don gian.

Ve sau, mot so flow nen chuyen sang event:

- co tin nhan moi -> tao notification
- tao review -> cap nhat trust score
- doi trang thai san pham -> gui thong bao

### 6. Service discovery noi bo khong can qua phuc tap

Voi quy mo hien tai chay Docker:

- khong can dung Eureka / Discovery Server
- co the dung Docker DNS noi bo

Vi du:

- `http://product-service:8080`
- `http://user-service:8080`
- `http://interaction-service:8080`
- `http://ai-service:8000`

Dieu nay du don gian va dung cho giai doan MVP.

## Kien truc de xuat cho MVP

Neu uu tien toc do ra san pham va do an:

- `api-gateway`
- `user-service`
- `product-service`
- `ai-service`
- `interaction-service`

Nhung can ap dung 3 dieu chinh thuc dung:

1. Authorization nghiep vu o tung service, khong dat het tai gateway.
2. `product-service` giu `catalog + listing + media + AI + search`.
3. `interaction-service` uu tien `chat + notification`, con `review` nen dua ve `user-service` hoac `product-service` tuy huong nghiep vu.
4. Frontend chi di qua `api-gateway`, bao gom ca API anh / media.
5. Service discovery noi bo dung Docker DNS, chua can Eureka.

## Rui ro neu di qua nhanh

1. Tach service qua som nhung khong co event contract ro rang.
2. Dung local upload roi scale nhieu instance se vo dong bo file.
3. Dung MongoDB cho ca review se kho xu ly aggregate va rang buoc hon Postgres.
4. Day het auth logic vao gateway se tao cam giac an toan gia.
5. De Frontend goi truc tiep vao service con thay vi gateway se lam vo design he thong.

## Ket luan

Kien truc nay hop ly va co the trien khai duoc.

Ngan gon:

- `ai-service` tach rieng la dung
- `product-service` la trai tim nghiep vu
- `user-service` nen so huu identity + profile
- `api-gateway` chi la lop ha tang va dieu phoi
- `interaction-service` can giu ranh gioi can than, nhat la phan review
- media va image URL nen di qua gateway
- service discovery tam thoi nen dung Docker DNS cho gon

Neu giu dung cac nguyen tac ownership, auth, va service boundaries nhu tren, day la mot nen tang tot de dua `H-Smart` tu MVP sang kien truc mo rong thuc te.
