# Chuẩn hóa lại dữ liệu OCR cũ

Trong quá trình indexing, AI service chuẩn hóa văn bản OCR trước khi trả kết quả. Tác vụ backfill ở backend mặc định bị tắt; chỉ cần chạy khi muốn sửa các bản ghi OCR cũ được tạo trước khi `normalized_text` được lưu nhất quán.

Chạy backfill thủ công bằng Docker Compose:

1. Dừng backend thông thường để bảo trì:

   ```powershell
   docker compose stop backend
   ```

2. Khởi chạy backend với tùy chọn backfill được bật:

   ```powershell
   docker compose run --rm -e APP_OCR_NORMALIZATION_ENABLED=true backend
   ```

3. Chờ log `OCR normalization backfill completed`, sau đó nhấn `Ctrl+C` để dừng container chạy backfill.
4. Khởi động lại backend thông thường:

   ```powershell
   docker compose up -d backend
   ```

Backfill đọc dữ liệu theo từng lô 500 bản ghi và chỉ lưu những bản ghi có nội dung chuẩn hóa thay đổi. Có thể chạy lại an toàn nếu lần trước bị gián đoạn. Không đặt `APP_OCR_NORMALIZATION_ENABLED=true` trong `.env` dùng cho backend thông thường, vì tác vụ bảo trì sẽ chạy mỗi lần backend khởi động.
