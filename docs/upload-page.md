# Upload Page — Thiết kế & API Spec

> Route: `/upload` · Fit 1 màn hình, không scroll · Dark-first · Bám design-system.md

---

## 1. Layout tổng thể

```
┌─────────────────────────────────────────────────────────┐
│  Header (56px, fixed, glass)                            │
├──────────────────────────────┬──────────────────────────┤
│  LEFT (55%)                  │  RIGHT (45%)             │
│                              │                          │
│  [Drop Zone]                 │  Index process           │
│                              │  ┌────────────────────┐  │
│  [Preview strip + View all]  │  │ 846 / 1000  ██░░░  │  │
│                              │  │ 200 / 500   ██████ │  │
│  [Upload button]             │  │ ...                │  │
│                              │  └────────────────────┘  │
│                              │  [View all →]            │
└──────────────────────────────┴──────────────────────────┘
```

- Toàn bộ content nằm trong `.page-container` (max-width 1280px, padding 0 24px).
- Hai cột dùng CSS Grid: `grid-template-columns: 55fr 45fr`, gap `var(--space-6)`.
- Chiều cao content area = `calc(100vh - 56px)`, dùng flex-column để phân bổ không gian.

---

## 2. Cột trái — Upload Zone

### 2.1 Header cột

```
Upload  [Library]           ← h2 nhỏ gọn + badge pill
```

- `h2`: `var(--text-xl)`, font-weight 600, color `var(--text-primary)`
- Badge `Library`: `.badge-brand`, border-radius `var(--radius-full)`

### 2.2 Drop Zone

**Trạng thái mặc định:**

```
┌ - - - - - - - - - - - - - - - - ┐
|        ☁ (icon, violet)         |
|    Drop images here             |
|    or browse files (violet)     |
|    [Ctrl+V]                     |
|   JPEG · PNG · WEBP  max 1,000  |
└ - - - - - - - - - - - - - - - - ┘
```

- Border: `2px dashed var(--border-medium)`, border-radius `var(--radius-2xl)`
- Background: `var(--bg-surface)`
- Flex-grow: chiếm phần lớn chiều cao còn lại của cột trái
- Icon cloud: `32px`, color `var(--brand)`
- Text "Drop images here": `var(--text-base)`, weight 600, white
- Text "or browse files": `var(--text-sm)`, color `var(--brand-hover)`, underline
- Kbd `Ctrl+V`: font-mono, 10px, border `1px solid var(--border-medium)`, bg `var(--bg-elevated)`, px 6px py 2px, radius `var(--radius-sm)`
- Constraints pills: `JPEG · PNG · WEBP` · `max 1,000` — `.badge`, `var(--text-xs)`, muted

**Trạng thái drag-over:**
```css
border-color: var(--brand);
background: oklch(0.55 0.24 268 / 0.06);
transform: scale(1.005);
box-shadow: 0 0 40px oklch(0.55 0.24 268 / 0.12),
            inset 0 0 20px oklch(0.55 0.24 268 / 0.04);
transition: all 200ms var(--ease-spring);
```

**Sự kiện hỗ trợ:**
- `ondragenter / ondragleave / ondragover / ondrop`
- `document.addEventListener('paste', ...)` — lọc `item.type.startsWith('image/')`
- `<input type="file" multiple accept="image/jpeg,image/png,image/webp" hidden>` — click để chọn

**Validation:**
- Chỉ nhận: `image/jpeg`, `image/jpg`, `image/png`, `image/webp`
- Từ chối: GIF, BMP — hiển thị toast error nhỏ
- Max mỗi file: `10MB` → toast warning nếu vượt
- Max tổng: `1000` ảnh → cắt bớt và toast warning
- Dedup: so sánh `name + size`, bỏ qua duplicate

### 2.3 Preview Strip

Hiển thị **ngay bên dưới drop zone** khi đã có ảnh được chọn. Ẩn khi chưa có ảnh.

```
[img7] [img6] [img5] [img4] [img3] [img2] [img1]   View all 24 →
```

- Horizontal scroll row, height `72px`, `overflow-x: auto`, `scrollbar-none`
- Hiển thị **tối đa 8 thumbnails**, sắp xếp từ mới nhất sang cũ nhất (reverse order)
- Mỗi thumbnail: `64×64px`, `object-fit: cover`, `border-radius: var(--radius-md)`
- Nút `×` nhỏ góc trên phải: xuất hiện on hover, xóa ảnh đó khỏi queue
- Nút `View all 24 →` ở cuối dãy: `var(--text-xs)`, color `var(--brand-hover)`, mở modal "All images"

**Khi không có ảnh**: strip này ẩn hoàn toàn (không giữ chỗ).

### 2.4 Upload Button

Hiển thị **ở dưới cùng cột trái** khi đã có ảnh. Ẩn khi chưa có ảnh.

```
[ ☁ Upload 24 images ]
```

- Full width cột trái, height `48px`
- Class `.btn.btn--primary`: `background: var(--gradient-brand)`, `box-shadow: var(--shadow-glow)`
- Hover: `translateY(-1px)` + tăng glow
- Icon upload cloud `16px` + text `Upload {n} images`

---

## 3. Cột phải — Index Process

### 3.1 Section Header

```
Index process                View all →
```

- Text "Index process": `var(--text-sm)`, weight 500, color `var(--text-secondary)`
- Link "View all →": `var(--text-xs)`, color `var(--brand-hover)`, mở modal "All batches"

### 3.2 Batch Card (compact)

Hiển thị **tối đa 6 cards** (batch đang trong trạng thái PROCESSING), sort theo `createdAt DESC`.

```
┌──────────────────────────────────────┐
│ ●  846 / 1000  ████████░░░░  2m ago │
└──────────────────────────────────────┘
```

- Height: `52px`, padding `0 var(--space-4)`
- Background: `var(--bg-surface)`, border `1px solid var(--border-subtle)`, radius `var(--radius-lg)`
- Dấu `●` pulsing: `width: 8px; height: 8px; border-radius: 50%; background: var(--brand); animation: pulse-glow 2s ease infinite`
- `846 / 1000`: `var(--text-sm)`, font-weight 600, font-mono, color `var(--text-primary)`, min-width đủ để không nhảy layout
- Progress bar: height `3px`, track `var(--bg-elevated)`, fill `var(--gradient-brand)`, border-radius full
- Timestamp: `var(--text-xs)`, color `var(--text-muted)`, right-aligned

**Khi không có batch đang chạy:**

```
┌─────────────────────────────┐
│   Không có tiến trình nào   │
└─────────────────────────────┘
```

Text muted, căn giữa, height `52px`.

### 3.3 Polling

- Khi mount page: gọi `GET /api/uploads/batches?status=PROCESSING&page=0&size=6&sort=createdAt,desc` một lần để lấy danh sách ban đầu.
- Sau đó **mỗi 5 giây** gọi lại để refresh. Dùng `setInterval`, clear khi unmount.
- Sau khi upload thành công: gọi lại ngay lập tức (không cần đợi 5s).
- Nếu API lỗi: giữ nguyên data cũ, không crash UI.

---

## 4. Modal — "View all images"

Hiển thị khi user ấn "View all {n} →" trên preview strip.

- Overlay: `var(--bg-overlay)`, backdrop-filter blur 8px
- Panel: max-width `640px`, max-height `80vh`, background `var(--bg-elevated)`, radius `var(--radius-2xl)`, `var(--shadow-modal)`
- Header: "24 images" + nút đóng `×`
- Body: grid `5 columns`, gap `var(--space-2)`, scroll dọc
- Mỗi ảnh: `aspect-ratio: 1`, `object-fit: cover`, radius `var(--radius-md)` + nút `×` hover để xóa
- Entrance: `scale-in` + backdrop fade-in

---

## 5. Modal — "View all batches"

Hiển thị khi user ấn "View all →" ở section Index process.

- Panel: max-width `520px`, max-height `75vh`
- Header: "Index process"
- Body: danh sách batch cards (giống compact card nhưng hơi cao hơn ~64px), chỉ batch status PROCESSING
- **Phân trang**: `page size = 10`, navigation đơn giản `< 1 / 3 >`
- API: `GET /api/uploads/batches?status=PROCESSING&page={p}&size=10&sort=createdAt,desc`
- Không có tìm kiếm, không có filter

---

## 6. Modal — Upload Progress (non-dismissable)

Hiển thị ngay sau khi user ấn "Upload {n} images". Không thể đóng.

```
┌───────────────────────────────┐
│    ☁                          │
│    Uploading                  │
│    846 / 1000                 │
│  ████████████░░░░░░  84%      │
└───────────────────────────────┘
```

- Overlay full-screen, `z-index: var(--z-modal)`, không có nút đóng, không click-outside để đóng
- Icon upload cloud `40px`, gradient indigo
- Text "Uploading": `var(--text-lg)`, weight 600
- Counter `846 / 1000`: font-mono, `var(--text-3xl)`, weight 700
- Progress bar: height `6px`, animated fill gradient brand
- Tự động đóng sau 600ms khi hoàn thành → trigger refresh batch list

---

## 7. Toast Notifications

Dùng `.toast` theo `components.css`:

| Trigger | Type | Message |
|---|---|---|
| File type không hợp lệ | error | `"tên-file.gif không hỗ trợ"` |
| File > 10MB | warning | `"tên-file.jpg vượt 10MB"` |
| Vượt 1000 ảnh | warning | `"Đã đạt tối đa 1,000 ảnh"` |
| Upload thành công | success | `"24 ảnh đã được tải lên"` |
| Upload lỗi | error | `"Lỗi kết nối, thử lại"` |

---

## 8. API Contracts

> Pattern: `BaseResponse<T>` — payload luôn trong `.data`. Dùng `request()` từ `apiClient.ts`.

### 8.1 Khởi tạo batch

```
POST /api/uploads/batches/init
```

**Request body:**
```json
{ "totalImages": 24 }
```

**Response `data`:**
```json
{ "batchId": "uuid" }
```

### 8.2 Upload ảnh theo batch

```
POST /api/uploads/batches/{batchId}/images?isLast=false
Content-Type: multipart/form-data
```

**FormData fields:**
- `files`: danh sách File (mỗi request gửi tối đa 1 chunk, ví dụ 50 ảnh)

**Query param `isLast`**: `true` cho chunk cuối cùng, `false` cho các chunk trước.

**Response `data`:**
```json
{
  "batchId": "uuid",
  "uploadedCount": 50,
  "failedCount": 0
}
```

### 8.3 Lấy danh sách batch đang xử lý

```
GET /api/uploads/batches?status=PROCESSING&page=0&size=6&sort=createdAt,desc
```

`page` và `size` là tham số bắt buộc do frontend chọn. Trang chính dùng `page=0&size=6`; modal dùng `size=10` và `page` đang xem. Hai nơi dùng cùng một API và cùng cấu trúc phản hồi.

**Response `data`:**
```json
{
  "content": [
    {
      "batchId": "uuid",
      "processedImages": 846,
      "totalImages": 1000,
      "status": "PROCESSING",
      "createdAt": "2026-10-01T15:00:00Z"
    }
  ],
  "totalPages": 5,
  "totalElements": 28,
  "number": 0
}
```

### 8.4 Dùng trong modal "View all"

```
GET /api/uploads/batches?status=PROCESSING&page=0&size=10&sort=createdAt,desc
```

Response `data` có cùng cấu trúc với mục 8.3.

### 8.5 Lấy chi tiết 1 batch (tùy chọn, dùng nếu cần)

```
GET /api/uploads/batches/{batchId}
```

**Response `data`:** object batch đơn lẻ (cùng schema như trên).

---

## 9. Service file gợi ý

```
frontend/src/services/uploadService.ts
```

Export các function:
```ts
initBatch(totalImages: number): Promise<{ batchId: string }>
uploadChunk(batchId: string, files: File[], isLast: boolean): Promise<ChunkResult>
listBatches(page: number, size: number): Promise<PagedResult<BatchSummary>>
```

Dùng `request()` từ `apiClient.ts`, không gọi axios trực tiếp.

---

## 10. State machine — Upload flow

```
IDLE
 │
 ├─ [user chọn ảnh] ──────────────────────────────► FILE_SELECTED
 │                                                      │
 │                                              [ấn Upload]
 │                                                      │
 │                                                      ▼
 │                                                UPLOADING (modal hiện, non-dismissable)
 │                                                      │
 │                                               [chunk hoàn thành]
 │                                                      │
 │                                               [isLast = true done]
 │                                                      │
 │                                                      ▼
 │                                              UPLOAD_DONE
 │                                              (modal đóng sau 600ms)
 │                                              (refresh batch list ngay)
 │                                                      │
 └──────────────────────────────────────────────────────┘
                                                  ▼
                                                IDLE (reset selectedFiles)
```

---

## 11. Responsive (tối thiểu)

- Desktop (≥1024px): layout 2 cột như thiết kế
- Tablet (768–1023px): 2 cột, tỉ lệ `50/50`
- Mobile (<768px): stack 1 cột, cột phải (Index process) xuống dưới, giới hạn 3 batch cards

---

*Cập nhật: 2026-10-01 · Route: `/upload` · Dự án: Visual Search Engine*
