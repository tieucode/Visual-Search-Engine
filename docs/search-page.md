# Trang tìm kiếm — frontend

Triển khai trong `frontend/src/pages/SearchPage`, dùng token và component chung theo
`design-system.md` v2.2: nền xanh nhạt, header gọn, tối đa không gian hiển thị ảnh.

## Bố cục và đường dẫn

- `/`: lưới ảnh đề xuất mẫu tự tải ngay khi vào trang; chưa có API cá nhân hóa.
- `/?mode=image`: ảnh truy vấn bo góc nằm bên trái, không panel/nút/chú thích;
  cột trái 1/3, ảnh ghim khi cuộn, vùng dưới ảnh để trống. Kết quả masonry chỉ ở
  cột phải. Mobile đặt ảnh truy vấn trước, không ghim để tránh che kết quả.
- `/?mode=text&q=...`: tìm theo mô tả, hiện dùng dữ liệu mẫu.
- `/?mode=ocr&q=...`: tìm chữ trong ảnh, hiện dùng poster mẫu.
- `/images/:imageId`: ảnh chính bo góc, hai icon phóng to và trái tim nổi góc dưới
  phải trên nền blur, ảnh tương tự xếp xung quanh. Nút phóng to mở `ImageViewer`
  dùng chung với trang upload: chỉ hiện ảnh bo góc vừa viewport, không panel nền;
  ứng dụng phía sau tối 72% và blur 8 px. Focus được giữ trong dialog và khóa cuộn nền. Nút đóng, Escape
  hoặc nhấn ngoài ảnh đóng dialog mà không rời trang chi tiết, trả focus về nút mở.
  Ảnh chính hiển thị độc lập với request tìm tương tự; vùng kết quả có skeleton.
  Chỉ có “Quay lại” phía trên; Escape cũng trở về trang trước khi không đang nhập
  hoặc mở dialog/dropdown. Quay lại giữ các trang đã tải và vị trí cuộn.
  Không có thông tin kích thước, thanh gợi ý, bộ đếm, bình luận hoặc chia sẻ.

Thanh tìm kiếm nằm trên header dùng chung cho các trang đã đăng nhập. Icon chế độ
mở dropdown “Mô tả” / “Chữ trong ảnh” bằng hover, click hoặc bàn phím. Placeholder
lần lượt là “mô tả bức ảnh mong muốn” / “chữ trong ảnh bạn muốn tìm”. Icon camera
chỉ bật/tắt hộp tải ảnh dưới header; không đổi chế độ hoặc nội dung ô nhập. Dialog
đóng bằng icon camera, nút đóng, nhấn ngoài hoặc Escape. Chọn tệp, kéo thả hoặc
Ctrl+V ảnh hợp lệ sẽ đóng dialog, chuyển đến `/?mode=image` và POST ngay, không
cần xác nhận thêm. File không hợp lệ giữ dialog mở và báo lỗi ngắn.

Text/OCR và đề xuất vẫn dùng mock, không thêm nhãn/chú thích vào lưới ảnh.
Dữ liệu Text/OCR gồm 36 ảnh mỗi loại, chia thành các
trang 12 ảnh với độ trễ giả lập; từ khóa thay đổi thứ tự minh họa, chưa thực hiện
tìm kiếm ngữ nghĩa/OCR. Ảnh Text lấy từ Unsplash; poster OCR được dựng tại frontend.
Nút thả tim chỉ giữ trạng thái trong phiên giao diện, chưa gọi API lưu yêu thích.
Đề xuất dùng adapter riêng `recommendedDemo` (36 ảnh, 20 ảnh/trang) để thay bằng
API recommend sau này. Container dùng hết chiều rộng với lề 24 px desktop / 12 px
mobile; ảnh cách header 12 px / 8 px. Header cao 64 px desktop, 108 px mobile.
Lưới tối đa 6 cột, mục tiêu 280 px/cột; mobile giữ 2 cột để ảnh dễ xem hơn.
Toàn bộ giao diện tăng cỡ chữ bằng root font-size 106.25%, giữ font Inter và fallback thiết bị.

## API và phân trang

`services/searchService.ts` dùng `request()` và envelope của backend:

1. `POST /search/image?size=20`, multipart `file`, ngay khi dialog nhận ảnh hợp lệ.
2. `GET /search/image/{searchId}/results?cursor=...` khi sentinel cách cuối trang
   650 px. Giữ nguyên cursor, không gửi lại ảnh hoặc page size.
3. Mỗi feed chỉ có một request tải tiếp. Append loại trùng theo `imageId`.
   `hasMore=false` kết thúc tải; lỗi giữ dữ liệu/cursor và có nút thử tải thêm.
4. Gửi truy vấn mới hủy request cũ. Chỉ đổi icon Text/OCR chưa gửi truy vấn.
   AbortController và generation ngăn response
   cũ ghi đè kết quả mới. Lỗi 401 cung cấp đường dẫn đăng nhập lại.

Timeout riêng cho tìm kiếm là 90 giây để phù hợp thời gian xử lý AI. Client kiểm
tra JPG/PNG/WebP, tối đa 10 MiB, 40 triệu pixel và 10.000 px mỗi chiều trước POST.
`score` không được hiển thị như phần trăm xác suất.

## Những phần phụ thuộc backend

Backend hiện chưa có API lấy ảnh theo ID hoặc tìm tương tự theo ID. Vì vậy:

- Điều hướng truyền metadata ảnh qua router state, đồng thời cache tối đa 200 ảnh
  trong sessionStorage. Refresh trang chi tiết trong cùng tab dùng metadata này;
  ảnh demo còn có thể mở trực tiếp từ manifest. Một URL ảnh thật mở trong phiên
  mới chưa có metadata sẽ hiển thị thông báo và liên kết về tìm kiếm. Cần endpoint
  lấy chi tiết ảnh để hỗ trợ permalink độc lập và làm mới signed URL hết hạn.
- Tìm tương tự đọc ảnh gốc từ signed URL rồi gửi vào API tìm bằng ảnh hiện có.
  Media origin cần cho phép CORS GET từ frontend. Mỗi lần mở ảnh thật tạo một
  phiên tìm kiếm mới; tải tiếp vẫn dùng GET của phiên đó. Ảnh chính bị loại khỏi
  kết quả. Lỗi media/CORS/API chỉ ảnh hưởng danh sách tương tự, không chặn ảnh chính.
  Khi backend có API theo ID, thay adapter `searchSimilarImages` để tránh tải và
  upload lại ảnh, đồng thời hỗ trợ ảnh gốc vượt giới hạn truy vấn hiện tại.
- Cache ảnh bị xóa khi đăng xuất hoặc token hết hạn. File truy vấn và object URL
  chỉ tồn tại trong bộ nhớ của workspace và được giải phóng khi thay ảnh/rời phiên.
  Refresh URL tìm bằng ảnh không giữ file: hiện lại lưới đề xuất và mở hộp chọn ảnh.

## Kiểm tra

`npm run build` kiểm tra TypeScript và production bundle.

`frontend/tests/search.smoke.mjs` là kiểm thử trình duyệt với Playwright. Chạy
Vite trước, cung cấp `SEARCH_TEST_URL` nếu khác `http://127.0.0.1:3000`.
Script dùng package `playwright`, hoặc đường dẫn package qua `PLAYWRIGHT_MODULE`;
`CHROME_PATH` có thể chỉ tới Chromium/Chrome đã cài. Chạy:

```text
node tests/search.smoke.mjs
```

Kiểm tra lưới đề xuất, header/dropdown/placeholder, bật/tắt dialog, chọn tệp/kéo thả/
dán ảnh tự tìm (kể cả từ trang upload), Text/OCR, infinite scroll, ảnh chi tiết/thả tim,
refresh/quay lại, vị trí cuộn, multipart/token, cursor nguyên vẹn, retry,
loại trùng, response cũ và viewport desktop/mobile. API ảnh được giả lập trong
trình duyệt; kiểm thử này không thay thế kiểm thử tích hợp với AI/Qdrant/MinIO thật.
Ảnh chụp lưu trong thư mục tạm `visearch-search-smoke`, có thể đổi bằng
`SEARCH_TEST_OUTPUT`.
