import { apiClient, request } from './apiClient';
import type { SearchImageData, SearchPageData } from '../types/search';

export async function searchByImage(file: File, signal: AbortSignal): Promise<SearchPageData> {
  const data = new FormData();
  data.append('file', file);
  const response = await request<SearchPageData, FormData>({
    method: 'POST', url: '/search/image', params: { size: 20 }, data, signal, timeout: 90_000,
  });
  return response.data;
}

export async function nextImagePage(page: SearchPageData, signal: AbortSignal): Promise<SearchPageData> {
  const response = await request<SearchPageData>({
    method: 'GET', url: `/search/image/${encodeURIComponent(page.searchId)}/results`,
    params: { cursor: page.nextCursor }, signal, timeout: 90_000,
  });
  return response.data;
}

/** Reuse the supported image-search API until an image-id similarity endpoint exists. */
export async function searchSimilarImages(image: SearchImageData, signal: AbortSignal): Promise<SearchPageData> {
  let response: Response;
  try {
    response = await fetch(image.imageUrl, { signal, credentials: 'omit' });
  } catch (error) {
    if (signal.aborted) throw error;
    throw new Error('Chưa thể đọc ảnh để tìm ảnh tương tự. Vui lòng thử lại sau.');
  }
  if (!response.ok) throw new Error('Ảnh đã hết hạn hoặc không còn khả dụng. Hãy tìm kiếm lại.');
  const blob = await response.blob();
  if (blob.size > 10 * 1024 * 1024) throw new Error('Ảnh vượt quá 10 MB nên chưa thể tìm ảnh tương tự.');
  const extension = blob.type === 'image/png' ? 'png' : blob.type === 'image/webp' ? 'webp' : 'jpg';
  return searchByImage(new File([blob], `similar.${extension}`, { type: blob.type || 'image/jpeg' }), signal);
}

export interface OcrSearchResult {
  imageId: string;
  imageUrl: string;
  thumbnailUrl: string;
  width: number | null;
  height: number | null;
  rawText: string;
  confidenceScore: number | null;
}


export async function searchByOcr(
  keyword: string,
  signal: AbortSignal,
): Promise<SearchPageData> {
  const response = await apiClient.get<OcrSearchResult[]>(
    '/search/ocr',
    {
      params: { q: keyword },
      signal,
      timeout: 30_000,
    },
  );

  if (!Array.isArray(response.data)) {
    throw new Error('API OCR không trả về danh sách kết quả hợp lệ.');
  }

  const items: SearchImageData[] = response.data.map(item => ({
    imageId: item.imageId,
    imageUrl: item.imageUrl,
    thumbnailUrl: item.thumbnailUrl || item.imageUrl,
    width: item.width,
    height: item.height,
    score: item.confidenceScore ?? 0,
  }));

  return {
    searchId: `ocr-${keyword}`,
    searchType: 'TEXT_OCR',
    items,
    size: items.length,
    hasMore: false,
    nextCursor: null,
  };
}
