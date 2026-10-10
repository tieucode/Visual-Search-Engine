# ViSearch Design System

Phiên bản 2.2 · Nền xanh dương nhạt, bố cục gọn, ưu tiên không gian hiển thị ảnh.

Tài liệu này quy định giao diện cho toàn bộ frontend. Các token thực tế nằm ở
`frontend/src/styles/tokens.css`; các thành phần dùng chung nằm ở
`frontend/src/components/common`.

## 1. Nguyên tắc

- Nền trang xanh dương rất nhạt; header, form, card và modal dùng nền trắng.
- Các khối chính nổi bật bằng nền trắng, bo góc 20 px và bóng nhẹ trên nền xanh.
- Dùng khoảng trắng, cỡ chữ và viền xám để phân chia nội dung bên trong khối.
- Màu xanh chỉ dùng cho thao tác chính, liên kết, focus và tiến trình.
- Mỗi vùng có một hành động chính. Nội dung ngắn, trực tiếp, bằng tiếng Việt.
- Trang đăng nhập chỉ có logo, tiêu đề, form và liên kết tạo tài khoản.
- Không dùng gradient trang, chữ gradient, glow, nền trang tối hoặc hình trang trí.
  Nền blur bán trong suốt dùng cho các nút nổi trên ảnh và lớp nền phía sau ảnh phóng to.
- Animation ngắn, chỉ hỗ trợ thay đổi trạng thái; không dùng hiệu ứng lặp để trang trí.
- Các trang chưa có chức năng đầy đủ hiển thị trạng thái rõ ràng và lối đi đến tính năng hiện có.

## 2. Thương hiệu

Tên hiển thị: **ViSearch**.

Logo gồm icon `ScanSearch` và chữ ViSearch màu chữ chính. Icon màu xanh nằm trong
ô trắng 32 × 32 px, viền xám, bo góc 8 px. Không dùng gradient hay đổ bóng cho logo.

Icon dùng Lucide, nét 1.5–2 px, kích thước thường 16–24 px. Icon mang tính trang trí
có `aria-hidden="true"`; thao tác chỉ có icon phải có tên truy cập bằng tiếng Việt.

## 3. Màu sắc

### Bề mặt và chữ

| Token | Giá trị | Sử dụng |
| --- | --- | --- |
| `--bg-base` | `#eef5ff` | Nền xanh dương nhạt toàn bộ trang |
| `--bg-surface` | `#ffffff` | Header, input, card, modal |
| `--bg-elevated` | `#f7faff` | Drop zone, hover, disabled, skeleton |
| `--bg-overlay` | `rgb(15 23 42 / 0.32)` | Lớp phủ phía sau modal |
| `--text-primary` | `#172033` | Tiêu đề, nội dung chính, nhãn input |
| `--text-secondary` | `#475569` | Nội dung hỗ trợ |
| `--text-muted` | `#64748b` | Placeholder, thời gian, chú thích |
| `--text-disabled` | `#94a3b8` | Điều khiển vô hiệu hóa |
| `--text-on-brand` | `#ffffff` | Chữ trên nút xanh hoặc đỏ |
| `--text-on-image` | `#ffffff` | Điều khiển phủ trên thumbnail |

`--bg-elevated` chỉ dùng cho vùng nhỏ, không dùng làm nền toàn trang.
Phóng to ảnh dùng vùng hiển thị trong suốt, chỉ có ảnh bo góc; giao diện phía sau
được phủ tối bằng `--bg-image-backdrop` và blur 8 px. Các modal form vẫn dùng panel trắng.

### Thao tác và viền

| Token | Giá trị | Sử dụng |
| --- | --- | --- |
| `--brand` | `#2563eb` | Nút chính, liên kết, progress |
| `--brand-hover` | `#1d4ed8` | Hover nút chính, liên kết |
| `--brand-active` | `#1e40af` | Nút chính đang nhấn |
| `--brand-tint` | `#eff6ff` | Tab đang chọn, drop zone đang tương tác |
| `--brand-tint-border` | `#bfdbfe` | Viền badge xanh, selection |
| `--border-subtle` | `#e2e8f0` | Viền card, header, modal |
| `--border-medium` | `#cbd5e1` | Viền input và vùng tải ảnh |
| `--border-strong` | `var(--brand)` | Input focus |
| `--focus-ring` | `rgb(37 99 235 / 0.16)` | Vòng focus input |

### Trạng thái

| Trạng thái | Chữ/icon | Nền nhẹ |
| --- | --- | --- |
| Thành công | `--color-success: #15803d` | `--success-tint: #f0fdf4` |
| Cảnh báo | `--color-warning: #a16207` | `--warning-tint: #fffbeb` |
| Lỗi | `--color-error: #b91c1c` | `--error-tint: #fef2f2` |
| Thông tin | `--color-info: var(--brand)` | `--brand-tint` |

Luôn có nội dung hoặc tên truy cập để giải thích trạng thái, không chỉ dùng màu.
Nút xóa trên ảnh dùng `--image-scrim` với icon trắng để dễ nhìn trên ảnh sáng và tối.

## 4. Chữ và khoảng cách

Frontend dùng Inter từ Google Fonts qua `--font-sans`, gồm các weight 400/500/600/700.
`display=swap` hiển thị font thiết bị trong lúc tải; nếu Google Fonts không tải được,
giao diện tiếp tục dùng `system-ui`, `-apple-system`, `BlinkMacSystemFont`, `Segoe UI`, sans-serif.
Các điều khiển và nhãn phím tắt cũng dùng cùng font giao diện.
Root font-size là 106.25% (17 px khi font mặc định của trình duyệt là 16 px),
tăng đồng đều các cỡ chữ dùng rem và vẫn tôn trọng thiết lập font của thiết bị.

| Thành phần | Kích thước | Độ đậm |
| --- | --- | --- |
| Tiêu đề trang | 25.5–31.875 px, tùy viewport | 600 |
| Tiêu đề đăng nhập/đăng ký | 29.75 px | 600 |
| Tiêu đề section | 19.125–21.25 px | 600 |
| Nội dung/form | 17 px | 400 |
| Label, nút, điều hướng | 14.875 px | 500–600 |
| Thời gian, metadata, badge | 12.75 px | 400–500 |

Line-height nội dung 1.6; tiêu đề 1.25. Tiêu đề có letter-spacing `-0.025em`.
Số lượng và phần trăm dùng `font-variant-numeric: tabular-nums`.

Khoảng cách dùng token theo đơn vị 4 px:
`--space-1/2/3/4/5/6/8/10/12/16` tương ứng
`4/8/12/16/20/24/32/40/48/64 px`.

- Giữa label và input: 8 px.
- Giữa các trường trong form: 16 px.
- Giữa tiêu đề auth và form: 20 px.
- Giữa các phần nội dung trang: 12–16 px.
- Padding trang desktop: 24 px hai bên; mobile: 12 px.
- Form auth nằm trong panel trắng, padding 24 px desktop / 20 px mobile.
- Khoảng cách panel auth đến cạnh màn hình: tối thiểu 16 px.

## 5. Bo góc, bóng và chuyển động

| Token | Giá trị |
| --- | --- |
| `--radius-sm` | 6 px |
| `--radius-md` | 8 px |
| `--radius-lg` | 10 px |
| `--radius-xl` | 12 px |
| `--radius-2xl` | 20 px |
| `--radius-full` | 9999 px |

Nút và input dùng radius-md; card, drop zone và modal dùng radius-xl.
Panel auth, vùng tải ảnh, khối tiến trình và nội dung trang chờ dùng radius-2xl.
Chỉ progress, nút điều hướng ảnh và chỉ số ảnh dùng dạng tròn hoặc pill.

- Card: `0 1px 2px rgb(15 23 42 / 0.04)`.
- Panel chính: `--shadow-panel: 0 2px 4px rgb(30 64 120 / 0.02), 0 12px 36px rgb(30 64 120 / 0.06)`.
- Card hover/toast: `0 4px 12px rgb(15 23 42 / 0.06)`.
- Modal: `0 16px 48px rgb(15 23 42 / 0.16)`.
- Form đăng nhập dùng bóng panel nhẹ; header, logo và nút chính không dùng bóng.
- Không xếp nhiều lớp bóng bên trong một panel. Hàng tiến trình và thumbnail chỉ cần viền.

Hover chuyển màu trong 150 ms. Modal xuất hiện trong 200 ms với scale 0.98 → 1.
Progress thay đổi chiều rộng mượt; skeleton có shimmer trung tính khi chờ dữ liệu.
Tôn trọng `prefers-reduced-motion`. Không làm card/nút nhảy lên hoặc icon nổi liên tục.

## 6. Thành phần dùng chung

### Button

- Chiều cao tối thiểu 44 px; phiên bản large 48 px.
- Primary: xanh đặc, chữ trắng.
- Outline: nền trắng, viền xám, chữ tối.
- Ghost: nền trong suốt, hover xám rất nhẹ.
- Destructive: nền đỏ, chữ trắng.
- Khi xử lý: disabled, `aria-busy`, nhãn trạng thái ngắn.
- Một hành động chính trong một vùng; không dùng gradient hoặc glow.

### Input

- Nền trắng, cao tối thiểu 46 px, viền medium, bo góc 8 px.
- Label luôn hiện phía trên; không thay label bằng placeholder.
- Focus viền xanh và vòng focus 3 px.
- Error viền đỏ, thông báo ngay dưới trường; dùng `aria-invalid` và `aria-describedby`.
- Disabled dùng nền xám nhẹ.
- Mật khẩu có nút hiện/ẩn 44 × 44 px, tên truy cập tương ứng.

### Card và badge

Card trắng, viền subtle, padding 24 px. Hover chỉ đổi viền và bóng nhẹ.
Badge dùng nền nhạt và chữ trạng thái đậm màu; không dùng nền tối, chữ trắng cho metadata.

### Modal và xem ảnh

Modal trắng, rộng tối đa 640 px; sm 520 px, static 360 px.
Header có tiêu đề và các thao tác; body cuộn riêng khi nội dung dài.
Panel không vượt vùng nhìn trên điện thoại. Nút đóng có vùng bấm 44 × 44 px.

Modal có `role="dialog"`, `aria-modal`, tên truy cập. Modal có thể đóng cho phép
Escape và nhấn ngoài panel; modal đang tải ảnh không đóng giữa chừng.

Tất cả lightbox/phóng to ảnh dùng chung `components/common/ImageViewer.tsx`.
Không có panel trắng, viền hoặc nền riêng sau ảnh. Ảnh giữ tỉ lệ, không cắt,
giới hạn vừa viewport và bo góc 20 px; nền ứng dụng tối 72% và blur 8 px.
Nút đóng phía trên; khi có nhiều ảnh, hiện điều hướng hai bên và bộ đếm phía dưới.
Hỗ trợ phím trái/phải, Escape và nhấn vùng trống để đóng; khóa cuộn nền,
giữ focus trong dialog và trả focus về vị trí mở. Áp dụng cả upload và chi tiết ảnh.

### Toast và progress

Toast trắng, viền xám, một cạnh màu trạng thái và icon tương ứng.
Hiển thị góc dưới bên phải, cách cạnh 24 px trên desktop, 16 px trên mobile.

Progress nền xám nhạt, fill xanh đặc, bo tròn; có `role="progressbar"`,
tên truy cập và giá trị hiện tại. Không thêm ánh sáng quét hoặc glow.

## 7. Bố cục trang

### Đăng nhập — `/login`

Một form tối đa 360 px nằm trong panel trắng rộng tối đa 410 px, đặt giữa viewport
trên nền xanh dương nhạt. Panel bo góc 20 px, viền nhẹ, dùng shadow-panel.
Nội dung theo thứ tự:

1. Logo ViSearch nhỏ.
2. Tiêu đề “Đăng nhập”.
3. Email.
4. Mật khẩu và nút hiện/ẩn.
5. Nút “Đăng nhập”.
6. Liên kết “Tạo tài khoản”.

Không có cột giới thiệu, tagline, mô tả tính năng, badge chào mừng, footer quảng cáo,
ảnh trang trí hoặc nhiều khung lồng nhau. Chỉ xuất hiện chữ bổ sung khi có lỗi hoặc đang xử lý.
Form được phép cuộn trên màn hình thấp.

### Đăng ký — `/register`

Dùng cùng bố cục đăng nhập. Thêm họ tên và xác nhận mật khẩu.
Tiêu đề/nút “Tạo tài khoản”, liên kết dưới form “Đăng nhập”.
Giữ kiểm tra mật khẩu xác nhận, autocomplete và trạng thái gửi form.

### Header

Header trắng cố định, cao 64 px desktop, viền xám phía dưới, không giới hạn 1280 px.
Logo bên trái, ô tìm kiếm chiếm phần rộng còn lại, tải ảnh và đăng xuất bên phải.
Ô nhập có icon tìm kiếm, icon chế độ Text/OCR và icon mở/đóng hộp tải ảnh.
Icon chế độ mở dropdown khi hover hoặc nhấn: “Mô tả” / “Chữ trong ảnh”. Không hiện
tên chế độ bên ngoài dropdown. Placeholder tương ứng: “mô tả bức ảnh mong muốn” /
“chữ trong ảnh bạn muốn tìm”. Nhấn Enter hoặc icon tìm kiếm để gửi truy vấn.
Icon tìm bằng ảnh độc lập với chế độ Text/OCR; nhận ảnh hợp lệ là tự tìm ngay.
Dưới 640 px, ô nhập nằm ở hàng thứ hai, header cao 108 px. Icon có tên truy cập
và vùng bấm 44 × 44 px; label ô nhập được ẩn trực quan để giữ header gọn.

### Tải ảnh — `/upload`

Nội dung dùng toàn bộ chiều rộng, cách header 16 px desktop / 12 px mobile.

- Không có tiêu đề hiển thị phía trên hai panel; h1 chỉ dành cho trình đọc màn hình.
- Desktop: vùng chọn ảnh bên trái, vùng “Đang xử lý” rộng 340 px bên phải.
- Tablet: cột tiến trình rộng 300 px.
- Hai panel luôn lấp đầy chiều cao còn lại dưới header đến lề dưới; drop zone giãn
  theo không gian còn lại, thumbnail và nút tải nằm cuối panel. Tiến trình cuộn riêng.
- Dưới 768 px: hai panel xếp dọc theo tỉ lệ 3:2 trong cùng viewport. Nội dung dài
  cuộn trong từng panel để cả vùng tải và tiến trình luôn có mặt trên màn hình.
- Vùng tải ảnh và khối tiến trình là hai panel trắng riêng, bo góc 20 px, bóng nhẹ.
- Hai panel có padding 16 px desktop / 12 px mobile.
- Drop zone dùng nền `--bg-elevated`, viền nét đứt mảnh, cao tối thiểu 200 px desktop / 180 px mobile.
- Icon tải ảnh màu xanh trong ô xanh nhạt; nút tải lên xanh đặc là hành động nổi bật nhất.
- Nội dung: icon tải ảnh, “Kéo ảnh vào đây hoặc chọn ảnh”, định dạng và phím dán.
- Khi đã chọn ảnh: bộ đếm/dung lượng, thumbnail 72 px và nút tải lên có số lượng.
- Giữ thao tác kéo thả, chọn nhiều ảnh, dán clipboard, bỏ ảnh, xem toàn bộ và phóng to.
- Tiến trình xử lý: mỗi hàng trắng, viền nhẹ, số đã xử lý/tổng, progress và thời gian.
- Không có tiến trình: “Chưa có ảnh đang xử lý”.
- Modal tải ảnh hiển thị số đã tải/tổng và phần trăm.

### Tìm kiếm — `/` và chi tiết — `/images/:imageId`

Nội dung toàn chiều rộng, bắt đầu sau header 12 px desktop / 8 px mobile.
Không có tiêu đề, form, tab, bộ đếm hoặc nhãn demo trong nội dung. Tiêu đề h1
chỉ dành cho trình đọc màn hình. Khi chưa tìm, hiện lưới ảnh đề xuất mẫu.

Kết quả masonry bo góc 12 px, khoảng cách 12 px desktop / 8 px mobile.
Tối đa 6 cột, chiều rộng mục tiêu mỗi cột 280 px; mobile giữ 2 cột.
Ảnh truy vấn nằm bên trái, hiển thị trực tiếp với góc bo tròn, không panel,
padding, tên tệp hay nút tìm thủ công. Ở trang tìm bằng ảnh, cột trái chiếm 1/3,
ảnh ghim dưới header khi cuộn; toàn bộ vùng bên dưới ảnh trái để trống. Kết quả
chỉ nằm trong cột phải. Mobile xếp ảnh truy vấn trước kết quả và bỏ ghim để tránh che ảnh.
Trang chi tiết chỉ có “Quay lại” phía trên. Ảnh chính hiển thị trực tiếp,
icon phóng to và trái tim xếp dọc ở góc dưới bên phải; nền trắng bán trong suốt,
blur 12 px, nút 48 × 48 px. Nếu trình duyệt không hỗ trợ blur, dùng nền trắng đặc.
Phóng to mở dialog xem ảnh vừa viewport, khóa cuộn nền, giữ focus trong dialog;
đóng bằng nút X, Escape hoặc nhấn ngoài ảnh và trả focus về nút mở.
Không có tiêu đề hoặc kích thước ảnh.
Mobile đặt ảnh chính trước danh sách. Ảnh chính không chờ API tìm tương tự.
Loading/hết dữ liệu ưu tiên icon và nội dung cho trình đọc màn hình; chỉ hiển thị
chữ khi cần thao tác hoặc báo lỗi. Chi tiết tại [search-page.md](./search-page.md).

### Các trang còn lại

`/batches` hiện là trang chờ của lịch sử xử lý: tiêu đề “Tiến trình xử lý”
và liên kết đến tiến trình hiện có ở trang tải ảnh.

Khi bổ sung tìm kiếm hoặc lịch sử, tiếp tục dùng nền xanh nhạt, panel trắng và thành phần chung.

## 8. Responsive và khả năng truy cập

Breakpoints: 640 / 768 / 1024 / 1280 px. Chiều rộng tối thiểu 320 px.
Không để nội dung dài làm tràn trang; grid dùng `minmax(0, 1fr)`.

- Tiêu đề chính mỗi trang dùng h1.
- Input có label rõ ràng, lỗi gắn đúng trường.
- Điều khiển chính có vùng bấm tối thiểu 44 px.
- Nút xóa thumbnail desktop có thể gọn hơn; trên thiết bị cảm ứng là 44 px.
- Focus luôn nhìn thấy; nút xóa ảnh xuất hiện cả khi focus bằng bàn phím.
- Nút/icon không phụ thuộc hover để dùng được trên mobile.
- Nội dung loading, lỗi và thành công có role/aria thích hợp.
- Không giảm độ tương phản của chữ bằng cách dùng token disabled cho nội dung bình thường.

## 9. Tổ chức mã nguồn

| File | Trách nhiệm |
| --- | --- |
| `styles/tokens.css` | Màu, font, khoảng cách, radius, shadow, layout token |
| `styles/global.css` | Reset, nền xanh nhạt, typography, container, skeleton, reduced motion |
| `styles/components.css` | Button, input, card, badge, drop zone, progress, toast |
| `styles/auth.css` | Form đăng nhập và đăng ký tối giản |
| `styles/layout.css` | Header, modal, lightbox |
| `styles/upload.css` | Bố cục tải ảnh, thumbnail và tiến trình |
| `styles/search.css` | Công cụ tìm kiếm, masonry, ảnh truy vấn và trang chi tiết |

Khi chỉnh style, ưu tiên token và thành phần chung. CSS trang chỉ xử lý bố cục
hoặc chi tiết riêng. Không thêm bộ màu độc lập trong từng trang.
