import { request } from './apiClient';
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

export async function searchByOcr(
  keyword: string,
  signal: AbortSignal,
): Promise<SearchPageData> {
  const response = await request<SearchPageData>({
    method: 'GET', url: '/search/ocr', params: { q: keyword, size: 20 }, signal, timeout: 90_000,
  });
  return response.data;
}

export async function nextOcrPage(page: SearchPageData, signal: AbortSignal): Promise<SearchPageData> {
  const response = await request<SearchPageData>({
    method: 'GET', url: `/search/ocr/${encodeURIComponent(page.searchId)}/results`,
    params: { cursor: page.nextCursor }, signal, timeout: 90_000,
  });
  return response.data;
}
