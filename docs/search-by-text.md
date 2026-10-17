# Search by text — tìm ảnh bằng mô tả

Tìm kiếm ngữ nghĩa trên toàn bộ kho ảnh `UPLOAD`, dùng cùng collection Qdrant và
luồng phân trang với [tìm bằng ảnh](search-by-image.md). Cả hai endpoint cần
`Authorization: Bearer <accessToken>`.

## Bắt đầu tìm kiếm

`POST /api/search/text?size=40`, `Content-Type: application/json`.

```json
{ "text": "A small house beside a lake" }
```

- `text`: bắt buộc, không chỉ chứa khoảng trắng, tối đa 500 ký tự sau khi bỏ
  khoảng trắng đầu/cuối. Backend lưu mô tả đã chuẩn hóa vào lịch sử.
- `size`: mặc định 40, từ 1 đến 100; cố định cho các trang của phiên tìm kiếm.

Backend gọi **`POST /api/embedding/text`** của AI service với `{ "text": "..." }`.
Đây là đường dẫn thực tế trong `ai-service/app/api/embedding.py`, không phải
`/embed-text`. Response AI phải có `success=true`, `dimension` khớp collection và
`embedding` là vector hữu hạn, khác vector không. AI dùng giới hạn token của
tokenizer khi xử lý mô tả dài (`truncation=True`); giới hạn 500 ký tự của API
không đảm bảo toàn bộ mô tả nằm trong giới hạn token của model.

Sau khi truy vấn Qdrant thành công, backend tạo đúng một bản ghi
`search_history.search_type=TEXT_SEMANTIC`, lưu `query_text` và thời gian xử lý.
Không tạo bản ghi ảnh hay object truy vấn. Kết quả rỗng vẫn tạo lịch sử;
lỗi AI/Qdrant không tạo lịch sử. Không cần đổi schema database.

Response HTTP **201**:

```json
{
  "status": 201,
  "message": "Text search created",
  "timestamp": "2026-10-17T00:00:00Z",
  "data": {
    "searchId": "8395d0d2-cdf3-41e6-9c3a-31b530592806",
    "searchType": "TEXT_SEMANTIC",
    "items": [
      {
        "imageId": "06962d28-4361-42ca-b80c-c8c055d057fa",
        "imageUrl": "https://media.example.com/images/a.jpg?<signature>",
        "thumbnailUrl": "https://media.example.com/thumbnails/a_thumb.jpg?<signature>",
        "width": 1200,
        "height": 800,
        "score": 0.31
      }
    ],
    "size": 40,
    "hasMore": false,
    "nextCursor": null
  }
}
```

`width`/`height` có thể `null`. `score` là cosine similarity, không phải xác suất;
không áp dụng ngưỡng điểm tối thiểu. Trang rỗng có `items=[]`, `hasMore=false`,
`nextCursor=null`. Không trả tổng số kết quả hoặc số trang.

## Cuộn tải thêm

`GET /api/search/text/{searchId}/results?cursor=<nextCursor>`

Response HTTP **200**, `message="Text search results retrieved"`, cùng kiểu
`data` với trang đầu. Backend kiểm tra owner và `TEXT_SEMANTIC`, xác thực cursor,
lấy mô tả đã lưu để gọi AI rồi truy vấn Qdrant. GET không tạo/cập nhật lịch sử.
Không gửi lại `text` hoặc `size`; retry dùng nguyên cursor trước đó.

Cursor HMAC chứa searchId, offset Qdrant và page size. Dùng chung cơ chế lookahead,
bù trang cho point đã xóa/mất dữ liệu và giữ thứ tự điểm với tìm bằng ảnh.
Không cache embedding hay snapshot; dữ liệu index thay đổi giữa các lần cuộn có
thể làm dịch thứ tự, trùng hoặc bỏ sót ảnh. Frontend loại trùng theo `imageId`.

Mỗi POST tạo phiên mới. POST chưa có idempotency key; nếu mất response sau khi
commit, thử lại lần tìm đầu có thể tạo lịch sử mới.

## Frontend

- Chế độ “Mô tả” gọi `searchByText`; `nextSearchPage` chọn URL tải tiếp theo
  `searchType` cho cả tìm bằng ảnh và mô tả.
- Dùng lại `useSearchFeed`, `FeedStatus`, masonry, cache metadata và trang chi tiết.
  Một request tải thêm tại mỗi thời điểm; lỗi giữ cursor để GET lại. AbortController
  và generation loại response cũ khi đổi truy vấn.
- Giữ route `/?mode=text&q=...`. Refresh URL tạo phiên mới; quay lại từ trang chi
  tiết giữ feed đã tải và vị trí cuộn. Khi đang tải trang đầu, khóa thao tác gửi mô tả.
- Giới hạn input và adapter cùng 500 ký tự. Adapter kiểm tra cả mô tả từ URL.
  API dùng timeout 90 giây như tìm bằng ảnh.
- OCR và đề xuất vẫn dùng dữ liệu mẫu.

## Mã dùng chung và lỗi

`AiEmbeddingClient` tạo và kiểm tra embedding cho cả ảnh và text;
`SimilaritySearchService` truy vấn/hydrate kết quả;
`SearchPaginationService` kiểm tra page size và dựng trang và ký/xác thực cursor;
`SearchHistoryService` ghi lịch sử và kiểm tra owner/type.
`TextSearchService` chỉ điều phối phần riêng của use case mô tả.

| HTTP | Trường hợp |
| --- | --- |
| 400 | JSON thiếu/sai, mô tả rỗng/quá dài, size ngoài giới hạn, cursor sai |
| 401 | Thiếu hoặc sai access token |
| 404 | Phiên không tồn tại/khác owner/khác type, mất mô tả truy vấn |
| 502 | AI/Qdrant/lấy URL ảnh thất bại hoặc embedding không hợp lệ |

Response lỗi dùng `BaseResponse<ApiErrorData>`; validation có `data.fieldErrors`.
Swagger mô tả đúng envelope, kiểu trang, trường nullable và HTTP 201/200.
Cấu hình AI/Qdrant/MinIO dùng chung với tìm bằng ảnh, không cần biến môi trường mới.

Kiểm tra build: `mvn -q -DskipTests compile` trong `backend/` và
`npm run build` trong `frontend/`.
