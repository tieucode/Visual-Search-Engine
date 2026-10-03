# Frontend base

Nguồn thiết kế: [design-system.md](./design-system.md). Code mới nằm trong `frontend/` (không dùng `frontend-tham-khao/`).

## UI

- `src/styles/tokens.css`: màu, chữ, spacing, radius, shadow, motion, layout và z-index. Dùng `var(--...)`; không hard-code màu hoặc inline style cho giá trị tĩnh.
- `src/styles/global.css`: reset, typography, container, glass, gradient text, animation và hỗ trợ reduced motion. Đã được import một lần trong `main.tsx`.
- `src/styles/components.css`: kiểu chung cho button, input, card, badge, drop zone, progress, toast, skeleton và image grid.
- `src/components/common`: import `Button`, `Input`, `Card`, `Badge` từ `../components/common` (điều chỉnh đường dẫn theo file). `Button` có `variant`, `size`, `loading`; `Input` có `label`, `error`. Dùng thẻ/thuộc tính HTML gốc còn lại.
- Layout: `.page-container` (1024px), `--form` (480px), `--centered` (720px), `--grid` (1280px). Dark mode mặc định.

## API

- `src/types/api.ts`: `BaseRequest<TBody>`, `BaseResponse<T>`, `ApiErrorData` khớp backend. `timestamp` là chuỗi ISO; lỗi validation ở `data.fieldErrors`.
- `src/services/apiClient.ts`: gọi `request<TResponse, TBody>({ method, url, data, params, signal })`; `url` bắt đầu bằng `/` và nằm dưới `VITE_API_BASE_URL` (mặc định `http://localhost:8080/api`). Kết quả là toàn bộ envelope, lấy payload qua `.data`. Axios tự xử lý JSON và `FormData`; không tự đặt `Content-Type` cho upload.
- `src/services/authToken.ts`: sau login/register gọi `setAccessToken(response.data.accessToken)`; token được giữ trong `sessionStorage` và gắn Bearer vào request ngoài `/auth/*`. HTTP 401 ở endpoint được bảo vệ sẽ xóa token; trang gọi API tự quyết định điều hướng. Logout gọi `clearAccessToken()`.
- Lỗi request được chuẩn hóa thành `ApiClientError` với `message`, `status`, `details`, `fieldErrors`; HTTP response của backend là nguồn message. Có thể truyền `signal` để hủy request.

```tsx
import { request, ApiClientError } from '../services/apiClient';

try {
  const { data } = await request<{ batchId: string }, { totalImages: number }>({ method: 'POST', url: '/batches/init', data: { totalImages: 2 } });
  console.log(data.batchId);
} catch (error) {
  if (error instanceof ApiClientError) console.error(error.message, error.fieldErrors);
}
```

Thêm endpoint theo từng domain trong `src/services/`; giữ request/response và token ở lớp dùng chung. Chạy `npm install` rồi `npm run build` trong `frontend/` để kiểm tra TypeScript và bundle.
Vite đọc `VITE_API_BASE_URL` lúc build; Docker nhận biến này qua build arg.
