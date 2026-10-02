# Visual Search Engine

Hệ thống lưu trữ hình ảnh và tìm kiếm thông minh hỗ trợ 3 phương thức:
1. **Image by Image**: Tìm kiếm hình ảnh tương đồng dựa trên đặc trưng hình ảnh.
2. **Image by Text (Semantic)**: Tìm kiếm hình ảnh dựa trên câu mô tả ngôn ngữ tự nhiên.
3. **Image by Text (OCR)**: Tìm kiếm hình ảnh có chứa văn bản khớp từ khóa.

---

## 🏗 Cấu Trúc Dự Án

```
Visual-Search-Engine/
├── frontend/                        # React + Vite + TypeScript
│   ├── public/                      # Static assets
│   ├── src/
│   │   ├── assets/
│   │   ├── components/
│   │   │   ├── common/              # Shared UI components
│   │   │   ├── layout/              # Header, Sidebar, Footer...
│   │   │   └── search/              # Search bars, image grids...
│   │   ├── pages/
│   │   │   ├── HomePage/
│   │   │   ├── SearchPage/
│   │   │   └── UploadPage/
│   │   ├── hooks/                   # Custom React hooks
│   │   ├── services/                # API calls
│   │   ├── store/                   # State management
│   │   ├── types/                   # TypeScript interfaces/types
│   │   └── utils/                   # Helpers
│   ├── Dockerfile
│   ├── nginx.conf
│   └── package.json
│
├── backend/                         # Java Spring Boot Core
│   ├── src/main/java/com/visualsearch/
│   │   ├── client/                  # HTTP Client gọi AI Service
│   │   ├── config/                  # Security, RabbitMQ, MinIO config
│   │   ├── controller/              # REST Controllers
│   │   ├── dto/                     # Data Transfer Objects
│   │   ├── entity/                  # Database Entities
│   │   ├── event/                   # RabbitMQ message producers & consumers
│   │   ├── exception/               # Global exception handlers
│   │   ├── mapper/                  # Entity <-> DTO mappers
│   │   ├── repository/              # JPA Data Repositories
│   │   ├── service/                 # Business logic
│   │   └── util/                    # Utilities
│   ├── src/main/resources/          # application.yml
│   ├── Dockerfile
│   └── pom.xml
│
├── indexing-service/                # Java Spring Boot background worker
│   ├── src/main/java/               # Consumer, processor, clients, persistence
│   ├── src/main/resources/          # Cấu hình độc lập của worker
│   ├── Dockerfile
│   └── pom.xml
│
├── ai-service/                      # Python FastAPI (AI Processing)
│   ├── app/
│   │   ├── api/v1/endpoints/        # Embedding & OCR endpoints
│   │   ├── core/                    # App configuration & settings
│   │   ├── models/                  # SigLIP & EasyOCR model loaders
│   │   ├── schemas/                 # Pydantic schemas
│   │   └── services/                # AI embedding & OCR logic
│   ├── requirements.txt
│   └── Dockerfile
│
├── docker-compose.yml               # Production/All services stack
├── docker-compose.dev.yml           # Local development overrides
└── .env.example                     # Environment variables template
```

---

## 🚀 Khởi Động Với Docker Compose

### 1. Cấu hình biến môi trường
```bash
cp .env.example .env
```

### 2. Khởi chạy toàn bộ hệ thống
```bash
docker compose up -d
```

Backend là schema owner duy nhất và chạy với `ddl-auto=update`. Indexing Service chỉ
kiểm tra schema bằng `ddl-auto=validate`. Ở lần triển khai đầu tiên trên database
trống, hãy khởi động Backend một lần để tạo schema trước khi scale worker. Sau đó
worker không cần Backend API đang chạy để tiếp tục xử lý message.

Để chạy bốn worker độc lập, mỗi worker xử lý tuần tự một message:

```bash
docker compose up -d --scale indexing-service=4
```

Indexing Service không có `container_name`, vì vậy Docker Compose có thể scale. Mỗi
replica dùng manual ACK, `prefetch=1`, concurrency `1`; RabbitMQ phân phối tối đa bốn
message song song trên toàn cụm.

Retry dùng queue TTL 15 phút rồi dead-letter trở lại queue chính. Header AMQP nội bộ
`x-retry-attempt` là số lần retry đã thực sự bắt đầu, tối đa `3`. Khi AI circuit đang
mở, message vẫn được trì hoãn nhưng không tăng header và không tăng
`image_index.retry_count`.

Message nghiệp vụ chỉ có dạng:

```json
{
  "images": [
    { "imageId": "uuid", "imageUrl": "https://..." }
  ]
}
```

Worker gọi AI qua `POST /api/v1/indexing/batch` bằng multipart với các field lặp
`imageIds` và `images`. AI phải trả kết quả riêng theo `imageId` để worker xử lý
partial success. Phần hiện thực endpoint này thuộc AI Service và không nằm trong
Indexing Service.

Hai list multipart được ghép theo cùng vị trí. Response tối thiểu:

```json
{
  "results": [
    {
      "imageId": "uuid",
      "status": "SUCCESS",
      "embedding": [0.1, 0.2],
      "ocr": []
    },
    {
      "imageId": "uuid",
      "status": "FAILED",
      "error": {
        "code": "AI_ERROR",
        "message": "reason",
        "retryable": true
      }
    }
  ]
}
```

`retry_count` là số lần retry đã thực sự bắt đầu, không phải tổng số lần xử lý.
Lần xử lý đầu tiên có giá trị `0`; lỗi ảnh vĩnh viễn luôn kết thúc với giá trị `0`.

### 3. Danh sách các Service và Cổng kết nối

| Dịch vụ | Địa chỉ / Cổng | Mô tả |
|---|---|---|
| **Frontend** | `http://localhost:3000` | Giao diện React SPA |
| **Backend API** | `http://localhost:8080` | Spring Boot REST API |
| **Indexing Service** | nội bộ `8081` | Background worker; health tại `/health` |
| **AI Service** | `http://localhost:8000/docs` | FastAPI Swagger Documentation |
| **PostgreSQL** | `localhost:5432` | Cơ sở dữ liệu quan hệ |
| **Qdrant** | `http://localhost:6333/dashboard` | Vector Database Web UI |
| **MinIO Console** | `http://localhost:9001` | Quản lý Object Storage |
| **RabbitMQ Management** | `http://localhost:15672` | RabbitMQ Web Management Dashboard (user: `guest`, pass: `guest`) |

## 🔐 Xác thực API

Backend sử dụng JWT access token duy nhất; không phát hành hay hỗ trợ refresh token. Token mặc định hết hạn sau 24 giờ (`JWT_EXPIRATION_MS=86400000`). Đặt một `JWT_SECRET` dài ít nhất 32 ký tự trong `.env` trước khi khởi động backend.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Đăng ký: `POST /api/auth/register`
- Đăng nhập: `POST /api/auth/login`
- Upload: `POST /api/batches/{batchId}/images?isLast=false` (chọn ảnh tại trường `files` trong Swagger)
- Với API yêu cầu xác thực, gửi header: `Authorization: Bearer <accessToken>`

Swagger đã khai báo Bearer authentication cho các API cần bảo vệ. Dùng nút **Authorize** để dán access token sau khi đăng ký hoặc đăng nhập.

Mọi API trả về cùng cấu trúc: `{ "status": 200, "message": "...", "timestamp": "...", "data": { ... } }`. Payload riêng của từng API luôn nằm trong `data`.
