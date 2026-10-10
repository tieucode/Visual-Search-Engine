# Search by image — backend API

Tìm ảnh tương đồng trên **toàn bộ kho ảnh `UPLOAD` của hệ thống**. Hai endpoint đều
cần `Authorization: Bearer <accessToken>`. Lịch sử và ảnh truy vấn thuộc user khởi
tạo; user khác không được tải tiếp kết quả của phiên đó.

## 1. Bắt đầu tìm kiếm

`POST /api/search/image?size=20`, `Content-Type: multipart/form-data`.

- `file`: một ảnh JPEG, PNG hoặc WebP, mặc định tối đa 10 MiB và 40 triệu pixel;
  mỗi chiều không vượt 10.000 px theo giới hạn hiện có của AI service.
- `size`: số kết quả mỗi trang, mặc định 20, từ 1 đến 100.

```bash
curl -X POST 'http://localhost:8080/api/search/image?size=20' \
  -H 'Authorization: Bearer <accessToken>' \
  -F 'file=@query.jpg;type=image/jpeg'
```

Backend kiểm tra nội dung ảnh, gọi `POST /api/embedding/image` của AI service
(multipart field `file`), kiểm tra vector rồi truy vấn Qdrant. Backend lưu ảnh
truy vấn vào MinIO dưới `search/<uuid>.<format>`; trong một transaction ngắn,
tạo `images.type=SEARCH` và **một** bản ghi `search_history.search_type=IMAGE_ONLY`.
Không publish RabbitMQ hay đưa ảnh truy vấn vào collection tìm kiếm.

`searchId` chính là ID của bản ghi `search_history`. Không cần thêm bảng/session
cache hoặc thay đổi schema hiện tại. `executionDurationMs` ghi thời gian xử lý
lần đầu, không cộng dồn các lần cuộn. Tìm thành công nhưng không có kết quả vẫn
được ghi lịch sử. Lỗi AI/Qdrant không tạo lịch sử; nếu lưu DB thất bại, backend
thử xóa object truy vấn vừa upload.

Response HTTP **201** (ví dụ một trang kích thước 2):

```json
{
  "status": 201,
  "message": "Image search created",
  "timestamp": "2026-10-11T00:00:00Z",
  "data": {
    "searchId": "8395d0d2-cdf3-41e6-9c3a-31b530592806",
    "searchType": "IMAGE_ONLY",
    "items": [
      {
        "imageId": "06962d28-4361-42ca-b80c-c8c055d057fa",
        "imageUrl": "https://media.example.com/images/a.jpg?<signature>",
        "thumbnailUrl": "https://media.example.com/thumbnails/a_thumb.jpg?<signature>",
        "width": 1200,
        "height": 800,
        "score": 0.93
      },
      {
        "imageId": "4c81172e-7c84-43f0-9b15-d897502abfbe",
        "imageUrl": "https://media.example.com/images/b.jpg?<signature>",
        "thumbnailUrl": "https://media.example.com/images/b.jpg?<signature>",
        "width": 800,
        "height": 600,
        "score": 0.88
      }
    ],
    "size": 2,
    "hasMore": true,
    "nextCursor": "<opaque-token>"
  }
}
```

`score` là điểm cosine của collection hiện tại, không phải phần trăm xác suất.
Chưa áp dụng ngưỡng điểm tối thiểu. URL ảnh gốc và thumbnail đều được ký; nếu
không có thumbnail thì `thumbnailUrl` dùng URL ảnh gốc.

## 2. Tải thêm khi cuộn

`GET /api/search/image/{searchId}/results?cursor=<nextCursor>`

```bash
curl -G 'http://localhost:8080/api/search/image/<searchId>/results' \
  -H 'Authorization: Bearer <accessToken>' \
  --data-urlencode 'cursor=<nextCursor>'
```

Response HTTP **200**, payload `data` cùng cấu trúc trang đầu. Backend lấy ảnh
truy vấn đã lưu, gọi AI lấy embedding lại và truy vấn Qdrant. **Không insert hay
update lịch sử** ở endpoint này. Retry hoặc hai request tải cùng cursor vẫn dùng
cùng `searchId`, không tạo thêm lịch sử, không làm tăng vị trí lưu trên server.

Cursor mang phiên bản, `searchId`, offset Qdrant và page size, có chữ ký HMAC.
FE phải gửi nguyên token; không tự giải mã/sửa token, không gửi lại file hoặc
`size` ở request tải thêm. Đổi `JWT_SECRET` sẽ làm cursor cũ mất hiệu lực.

- `hasMore=true`: append `items`, lưu `nextCursor` mới để tải tiếp.
- `hasMore=false`, `nextCursor=null`: kết thúc, ngừng gọi API.
- Không trả `totalElements`/`totalPages`; giao diện không cần đếm toàn bộ kho.

Mỗi lần **POST** là một hành động tìm kiếm mới, kể cả ảnh giống lần trước. Vì vậy
FE chỉ POST khi user bắt đầu tìm mới; cuộn trang và retry tải thêm phải dùng GET.
Khóa nút tìm khi request đầu đang chạy. POST hiện chưa hỗ trợ idempotency key:
nếu response đầu bị mất sau khi commit và FE POST lại, sẽ tạo phiên mới.

Gợi ý điều phối FE (chưa triển khai frontend trong thay đổi này):

1. Khi tìm mới, hủy request tìm cũ, reset danh sách, POST ảnh và lưu `searchId`.
2. Khi sentinel của `IntersectionObserver` gần cuối trang xuất hiện, chỉ GET khi
   `hasMore && !isLoadingMore`; mỗi lần chỉ có một request tải thêm đang chạy.
3. Append và loại trùng theo `imageId`; bỏ response đến muộn nếu `searchId` không
   còn là phiên đang hiển thị. Khi tìm mới nên dùng thêm request generation/AbortController
   để response của POST cũ không ghi đè phiên mới.
4. Khi GET lỗi, giữ cursor hiện tại để retry; không POST để retry một trang.

## 3. Phân trang và giới hạn nhất quán

Dùng [Qdrant Query API](https://api.qdrant.tech/api-reference/search/query-points/)
`POST /collections/{collection}/points/query` với `query`, `offset`, `limit`,
`with_payload=false`, `with_vector=false` (yêu cầu Qdrant >= 1.10).
Điểm ID là UUID `images.id`, đúng contract của indexing worker hiện tại.
Đây là truy vấn độ tương đồng; không dùng endpoint `/points/scroll` để xếp hạng.

Backend lấy thêm một **ảnh hợp lệ** ngoài page size để xác định `hasMore`.
Các ID được hydrate theo batch từ PostgreSQL, chỉ lấy `ImageType.UPLOAD` và tự
lọc soft delete bằng `@SQLRestriction` hiện có. Giữ nguyên thứ tự/điểm Qdrant,
không lấy thứ tự của câu SQL `IN`. Nếu có point mất bản ghi hoặc ảnh đã xóa,
backend đọc thêm các batch Qdrant để bù trang. Offset cursor đếm **point Qdrant
đã đi qua**, không phải số item FE nhận được; ảnh lookahead không bị bỏ qua.

Hiện không cache embedding, danh sách ID hay snapshot kết quả. Vì vậy khi ảnh
được index/xóa/re-index hoặc model thay đổi giữa các request, thứ tự có thể dịch
chuyển, gây trùng hoặc bỏ sót kết quả qua các trang. FE loại trùng chỉ xử lý phần
trùng; muốn đảm bảo một tập kết quả bất biến cần snapshot/cache theo `searchId`
ở phiên bản sau. Offset lớn cũng tốn chi phí truy vấn hơn; một trang có nhiều
point đã xóa có thể cần nhiều lượt Qdrant. Đây là giới hạn của cách phân trang
chưa cache, không phải việc ghi thêm lịch sử.

## 4. Cấu hình và lỗi

Backend đọc cùng cấu hình collection/vector/model endpoint với indexing worker:

| Biến | Mặc định khi chạy backend local |
| --- | --- |
| `AI_SERVICE_BASE_URL` | `http://localhost:8000` |
| `AI_SERVICE_CONNECT_TIMEOUT` / `AI_SERVICE_TIMEOUT` | `3s` / `60s` |
| `QDRANT_BASE_URL` | `http://localhost:6333` |
| `QDRANT_COLLECTION` / `QDRANT_VECTOR_SIZE` | `images` / `768` |
| `QDRANT_CONNECT_TIMEOUT` / `QDRANT_REQUEST_TIMEOUT` | `3s` / `10s` |
| `SEARCH_MAX_IMAGE_BYTES` / `SEARCH_MAX_IMAGE_PIXELS` | `10485760` / `40000000` |
| `MINIO_PUBLIC_ENDPOINT` | fallback về `MINIO_ENDPOINT` |
| `MINIO_REGION` | `us-east-1` |

Compose đã truyền các biến này cho backend và dùng hostname nội bộ `ai-service`,
`qdrant` để gọi các dependency. `MINIO_PUBLIC_ENDPOINT` trong Compose mặc định
`http://localhost:9000`; khi triển khai, đặt thành origin mà **trình duyệt user
truy cập được**. Không sửa hostname của URL sau khi ký vì sẽ làm sai chữ ký.
`MINIO_REGION` phải khớp region của MinIO. Worker vẫn dùng URL nội bộ hiện có.

Collection phải được indexing worker khởi tạo và chứa vector cùng chiều/model.
Collection rỗng trả trang rỗng; collection chưa tồn tại là lỗi dependency 502.
Ảnh truy vấn được giữ lại để tải trang và phục vụ lịch sử; chưa thêm TTL/dọn lịch
sử tự động. Nếu thay đổi giới hạn upload thì cần điều chỉnh thêm giới hạn multipart
Spring khi cần (hiện `max-file-size=20MB`).

| HTTP | Trường hợp |
| --- | --- |
| 400 | thiếu file/cursor, size không hợp lệ, ảnh không đọc được/sai format, cursor sai phiên hoặc chữ ký |
| 401 | chưa đăng nhập hoặc token không hợp lệ |
| 404 | searchId không tồn tại, thuộc user khác, sai search type hoặc mất ảnh truy vấn |
| 413 | vượt giới hạn multipart của Spring |
| 502 | AI/Qdrant/MinIO thất bại hoặc dependency trả dữ liệu không hợp lệ |

Lỗi dùng envelope `BaseResponse` hiện có. Lỗi kích thước/pixel do validator nghiệp
vụ phát hiện trả 400. Lỗi DB dùng cơ chế lỗi hiện có của backend.

## 5. Mở rộng và kiểm thử

`ImageSearchService` điều phối use case ảnh; `SimilaritySearchService` nhận vector
nên có thể dùng lại cho semantic text. `SearchCursorService`, DTO trang và enum
`SearchType` có thể dùng chung; OCR sau này có thể có retriever riêng. Chưa thêm
endpoint OCR/text/click, chưa cache kết quả. `searchId` có thể dùng làm khóa ngoại
`SearchClick.searchHistory` khi bổ sung hành vi click sau này.

```bash
cd backend
mvn -q test
```

Kiểm thử bao gồm: tạo/tải lại phiên không nhân bản lịch sử; kiểm tra owner; ký
cursor; giới hạn ảnh; thứ tự điểm; lookahead; point đã xóa/mất dữ liệu; hết trang;
request/response AI và Qdrant; lỗi dependency; bù trừ MinIO; URL public có chữ ký;
endpoint được bảo vệ và envelope lỗi. Kiểm thử persistence chạy với H2 ở chế độ
PostgreSQL; HTTP dependency được giả lập. Vẫn cần smoke test với stack thật và
model khi Docker/dependency sẵn sàng.
