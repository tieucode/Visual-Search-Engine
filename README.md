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
│   ├── src/test/                    # Tests
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
├── infra/                           # Infrastructure configurations
│   ├── postgres/init.sql            # PostgreSQL database schema
│   ├── rabbitmq/                    # RabbitMQ configs
│   ├── qdrant/collection-config.json# Qdrant collection definition
│   └── minio/init-buckets.sh        # MinIO bucket setup
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

### 3. Danh sách các Service và Cổng kết nối

| Dịch vụ | Địa chỉ / Cổng | Mô tả |
|---|---|---|
| **Frontend** | `http://localhost:3000` | Giao diện React SPA |
| **Backend API** | `http://localhost:8080` | Spring Boot REST API |
| **AI Service** | `http://localhost:8000/docs` | FastAPI Swagger Documentation |
| **PostgreSQL** | `localhost:5432` | Cơ sở dữ liệu quan hệ |
| **Qdrant** | `http://localhost:6333/dashboard` | Vector Database Web UI |
| **MinIO Console** | `http://localhost:9001` | Quản lý Object Storage |
| **RabbitMQ Management** | `http://localhost:15672` | RabbitMQ Web Management Dashboard (user: `guest`, pass: `guest`) |
