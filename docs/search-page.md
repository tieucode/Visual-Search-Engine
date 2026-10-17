# Trang tìm kiếm — frontend

Triển khai trong `frontend/src/pages/SearchPage`, dùng token và component chung theo
`design-system.md` v2.2: nền xanh nhạt, header gọn, tối đa không gian hiển thị ảnh.

## Bố cục và đường dẫn

- `/`: lưới ảnh đề xuất mẫu tự tải ngay khi vào trang; chưa có API cá nhân hóa.
- `/?mode=image`: ảnh truy vấn bo góc nằm bên trái, không panel/nút/chú thích;
  cột trái 1/3, ảnh ghim khi cuộn, vùng dưới ảnh để trống. Kết quả masonry chỉ ở
  cột phải. Mobile đặt ảnh truy vấn trước, không ghim để tránh che kết quả.
- `/?mode=text&q=...`: tìm theo mô tả qua API thật, dùng text embedding và Qdrant.
- `/?mode=ocr&q=...`: tìm chữ trong ảnh, hiện dùng poster mẫu.
- `/images/:imageId`: ảnh chính bo góc, hai icon phóng to và trái tim nổi góc dưới
  phải trên nền blur, ảnh tương tự xếp xung quanh. Nút phóng to mở `ImageViewer`
  dùng chung với trang upload: chỉ hiện ảnh bo góc vừa viewport, không panel nền;
  ứng dụng phía sau tối 72% và blur 8 px. Focus được giữ trong dialog và khóa cuộn nền. Nút đóng, Escape
  hoặc nhấn ngoài ảnh đóng dialog mà không rời trang chi tiết, trả focus về nút mở.
  Ảnh nhỏ cũng được phóng vừa vùng xem và giữ nguyên tỉ lệ. Khung ảnh cố định khi
  cuộn chuột để zoom nội dung bên trong (1–5 lần), không thu nhỏ hơn mức ban đầu;
  kéo để di chuyển ảnh đã phóng lớn; phím `+`/`-` để zoom, `0` hoặc nhấn đúp để
  đưa về vừa màn hình. Thao tác này dùng chung với lightbox ở trang upload.
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

OCR và đề xuất vẫn dùng mock, không thêm nhãn/chú thích vào lưới ảnh.
Dữ liệu OCR gồm 36 poster, chia thành các trang 12 ảnh với độ trễ giả lập;
từ khóa thay đổi thứ tự minh họa, chưa thực hiện tìm kiếm OCR.
Ảnh đề xuất lấy từ Unsplash; poster OCR được dựng tại frontend.
Nút thả tim chỉ giữ trạng thái trong phiên giao diện, chưa gọi API lưu yêu thích.
Đề xuất dùng adapter riêng `recommendedDemo` (36 ảnh, 20 ảnh/trang) để thay bằng
API recommend sau này. Container dùng hết chiều rộng với lề 24 px desktop / 12 px
mobile; ảnh cách header 12 px / 8 px. Header cao 64 px desktop, 108 px mobile.
Lưới tối đa 6 cột, mục tiêu 280 px/cột; mobile giữ 2 cột để ảnh dễ xem hơn.
Toàn bộ giao diện tăng cỡ chữ bằng root font-size 106.25%, giữ font Inter và fallback thiết bị.

## API và phân trang

`services/searchService.ts` dùng `request()` và envelope của backend:

1. `POST /search/image?size=40`, multipart `file`, ngay khi dialog nhận ảnh hợp lệ.
   Mô tả dùng `POST /search/text?size=40`, JSON `{ "text": "..." }`; cả hai trả HTTP 201.
2. `nextSearchPage` dùng `searchType` để chọn
   `GET /search/image/{searchId}/results?cursor=...` hoặc
   `GET /search/text/{searchId}/results?cursor=...` khi sentinel cách cuối trang
   650 px. Giữ nguyên cursor, không gửi lại ảnh/mô tả hoặc page size.
3. Mỗi feed chỉ có một request tải tiếp. Append loại trùng theo `imageId`.
   `hasMore=false` kết thúc tải; lỗi giữ dữ liệu/cursor và có nút thử tải thêm.
4. Gửi truy vấn mới hủy request cũ. Chỉ đổi icon Text/OCR chưa gửi truy vấn.
   AbortController và generation ngăn response
   cũ ghi đè kết quả mới. Lỗi 401 cung cấp đường dẫn đăng nhập lại.

Timeout riêng cho tìm kiếm là 90 giây để phù hợp thời gian xử lý AI. Client kiểm
tra JPG/PNG/WebP, tối đa 10 MiB, 40 triệu pixel và 10.000 px mỗi chiều trước POST.
`score` không được hiển thị như phần trăm xác suất.
Mô tả được trim, giới hạn 500 ký tự ở cả input, adapter và backend. Khóa gửi mô tả
khi đang tải trang đầu để tránh tạo phiên trùng. Truy cập/refresh URL mô tả tạo
phiên mới; quay lại từ trang chi tiết tiếp tục dùng feed trong bộ nhớ.
Contract chi tiết tại [search-by-text.md](search-by-text.md).

## Những phần phụ thuộc backend

Backend chưa có API lấy metadata ảnh theo ID. Việc tìm tương tự đã có API riêng:

- Điều hướng truyền metadata ảnh qua router state, đồng thời cache tối đa 200 ảnh
  trong sessionStorage. Refresh trang chi tiết trong cùng tab dùng metadata này;
  ảnh demo còn có thể mở trực tiếp từ manifest. Một URL ảnh thật mở trong phiên
  mới chưa có metadata sẽ hiển thị thông báo và liên kết về tìm kiếm. Cần endpoint
  lấy chi tiết ảnh để hỗ trợ permalink độc lập và làm mới signed URL hết hạn.
- Tìm tương tự gọi `POST /api/search/image/{imageId}/similar?size=40`.
  Backend dùng vector đã có trong Qdrant, loại ảnh chính khỏi truy vấn và tạo phiên
  IMAGE_ONLY tham chiếu ảnh thư viện. Tải tiếp dùng GET `/api/search/image/{searchId}/results`.
  Không tải ảnh về blob hay upload lại, không cần CORS đọc media, không phụ thuộc
  MIME/dung lượng ảnh nguồn. Lỗi API chỉ ảnh hưởng danh sách tương tự.
- `imageUrl` và `thumbnailUrl` có thể thuộc origin bên ngoài. Grid/modal dùng thẻ
  img; thumbnail lỗi sẽ thử ảnh gốc. Nguồn ảnh phải cho phép trình duyệt hiển thị;
  URL HTTPS nên dùng khi frontend chạy HTTPS.
- Cache ảnh bị xóa khi đăng xuất hoặc token hết hạn. File truy vấn và object URL
  chỉ tồn tại trong bộ nhớ của workspace và được giải phóng khi thay ảnh/rời phiên.
  Refresh URL tìm bằng ảnh không giữ file: hiện lại lưới đề xuất và mở hộp chọn ảnh.

## Kiểm tra

`npm run build` kiểm tra TypeScript và production bundle.

Chưa có script kiểm thử trình duyệt tự động trong repository. Cần kiểm tra thủ công tìm kiếm, cursor, hủy request, infinite scroll và modal trên stack thật.
