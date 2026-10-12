
import { getAccessToken } from "./authToken";

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api";

export interface UploadHistoryItem {
  id: string;
  imageUrl: string;
  thumbnailUrl: string | null;
  width: number | null;
  height: number | null;
  fileSize: number | null;
  fileFormat: string;
  type: string;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

interface ApiResponse<T> {
  status: number;
  message: string;
  timestamp: string;
  data: T;
}

export async function getMyUploads(
  page = 0,
  size = 20
): Promise<PageResponse<UploadHistoryItem>> {
  const token = getAccessToken();

  if (!token) {
    throw new Error("Vui lòng đăng nhập để xem lịch sử upload.");
  }

  const response = await fetch(
    `${API_BASE_URL}/images/my-uploads?page=${page}&size=${size}`,
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    }
  );

  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      throw new Error("Phiên đăng nhập không hợp lệ hoặc đã hết hạn.");
    }

    throw new Error(
      `Không thể tải lịch sử upload (${response.status}).`
    );
  }

  const body: ApiResponse<PageResponse<UploadHistoryItem>> =
    await response.json();

  return body.data;
}
