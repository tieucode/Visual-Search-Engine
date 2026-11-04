# Import photos

Nhánh tạm `feat/import-photos` dành cho tác vụ này. Người dùng quyết định thời điểm xóa nhánh và khởi chạy batch thật.

Cần Java 21+, Maven, PostgreSQL có schema ứng dụng, AI service và Qdrant. Chạy từ gốc repo bằng PowerShell.

```powershell
# Đặt các biến này theo môi trường đang chạy nếu khác giá trị mặc định.
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:5432/visual_search_db'
$env:SPRING_DATASOURCE_USERNAME = 'postgres'
$env:SPRING_DATASOURCE_PASSWORD = 'postgres_password'
$env:QDRANT_BASE_URL = 'http://localhost:6333'
.\indexing-service\import-photos.ps1
```

Script build CLI rồi chạy độc lập. Mặc định dùng một instance AI chạy GPU tại `http://127.0.0.1:8002`. Mỗi nhóm tối đa 3 ảnh, hoặc nhỏ hơn theo `INDEXING_AI_BATCH_SIZE`, chỉ một nhóm được gửi đến GPU tại một thời điểm. Kết quả được commit theo đúng thứ tự CSV. Heap giới hạn 768 MiB. Dùng địa chỉ IPv4 trực tiếp để tránh độ trễ kết nối localhost đã quan sát khi benchmark trên máy này.

Các biến môi trường không được tự đọc từ `.env`. Có thể dùng các biến timeout, giới hạn ảnh, collection và vector size trong `application.yml`. Endpoint cho CLI được chọn bằng tham số `-AiUrls`, độc lập với `AI_SERVICE_BASE_URL` của worker. CLI hỗ trợ một hoặc hai endpoint nếu cần cấu hình riêng.

```powershell
.\indexing-service\import-photos.ps1 -AiUrls 'http://127.0.0.1:8002'
```

Instance GPU chạy bằng container `visual-search-ai-gpu-test`, cổng host `127.0.0.1:8002`, image `visual-search-ai-service:gpu-test`, với `DEVICE=cuda`, `TORCH_NUM_THREADS=4` và `EMBEDDING_MICROBATCH_SIZE=2`. Instance dùng GTX 1650, model SigLIP2 và EasyOCR en,vi, cùng volume cache model của hệ thống. Kiểm tra sẵn sàng và dừng khi hoàn thành tác vụ bằng các lệnh sau.

```powershell
Invoke-RestMethod http://127.0.0.1:8002/health
docker stop visual-search-ai-gpu-test
```

Container tạm không tự khởi động lại sau khi Docker restart. Dùng `docker start visual-search-ai-gpu-test` để bật lại và chờ `/health` trả thành công trước khi tiếp tục import. Nếu endpoint lỗi trong lúc chạy, các ảnh gửi tới endpoint đó được chốt FAILED, theo quy tắc không retry của importer.

Batch cố định `8c8379f6-8bf8-4c2a-9c16-71b9e22fcf78`, uploader `1d67f029-c68b-43fb-a884-92cba9801d7c`, 25.000 dòng. CSV được kiểm tra SHA-256 `d9f87e961bbbe72ede2f90e8d5179d3d8162f9c498a19c65f77b0d37148aaaa2` trước khi ghi. Snapshot metadata CSV giữ trong RAM để thứ tự ổn định trong lần chạy, byte ảnh chỉ giữ trong nhóm đang xử lý.

DB giữ nguyên URL ảnh và thumbnail, kích thước từ CSV. URL tải cho AI dùng `w=1024&h=1024&fit=max&fm=jpg`, giữ tỷ lệ toàn ảnh. `file_size` để NULL. `file_format` dựa trên đuôi URL, mặc định JPEG. Ba URL ngoài `images.unsplash.com` sẽ FAILED.

Dừng bằng Ctrl+C. Chạy lại cùng lệnh để tiếp tục từ `success_count + failed_count`, sau khi đối chiếu toàn bộ prefix đã commit. Nhóm chưa commit có thể được tính toán lại. UUID ảnh ổn định giúp Qdrant upsert cùng point. Một session PostgreSQL giữ khóa batch và thực hiện các transaction. Mất kết nối DB sẽ dừng lần chạy. Tránh tác vụ khác ghi vào batch này trong suốt quá trình import.

Mỗi lỗi ảnh được chốt FAILED với retry_count 0. Nếu Qdrant nhận point nhưng phản hồi bị mất, một ảnh FAILED có thể còn point trong Qdrant và có thể xuất hiện trong kết quả tìm kiếm. PostgreSQL và Qdrant không có transaction chung. Khi lỗi toàn tiến trình, mã thoát là 1. Khi đã xét hết CSV, mã thoát là 0 kể cả có ảnh FAILED. Log checkpoint chứa tổng success/failed. Batch chuyển COMPLETED khi tổng bằng 25.000, thời lượng theo thời gian từ created_at của batch như worker hiện tại.

Kiểm thử tích hợp dùng PostgreSQL riêng. Tạo database rỗng, đặt `PHOTO_IMPORT_TEST_DB` thành JDBC URL cùng `PHOTO_IMPORT_TEST_USER`, `PHOTO_IMPORT_TEST_PASSWORD`, rồi chạy `mvn test` trong thư mục indexing-service. Test tạo và xóa schema riêng cho từng ca. Khi không có biến môi trường DB, test tích hợp được đánh dấu skipped.
