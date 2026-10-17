import { request } from './apiClient';
import { parseSearchPage } from './searchResponse';
import { MAX_SEARCH_TEXT_LENGTH, type SearchImageData, type SearchPageData, type TextSearchRequest } from '../types/search';

const SEARCH_PAGE_SIZE = 40;
const SEARCH_TIMEOUT_MS = 90_000;

export async function searchByImage(file: File, signal: AbortSignal): Promise<SearchPageData> {
  const data = new FormData();
  data.append('file', file);
  const response = await request<SearchPageData, FormData>({
    method: 'POST', url: '/search/image', params: { size: SEARCH_PAGE_SIZE }, data, signal, timeout: SEARCH_TIMEOUT_MS,
  });
  return parseSearchPage(response.data);
}

export async function searchByText(text: string, signal: AbortSignal): Promise<SearchPageData> {
  const query = text.trim();
  if (!query) throw new Error('Hãy nhập mô tả bức ảnh muốn tìm.');
  if (query.length > MAX_SEARCH_TEXT_LENGTH) {
    throw new Error(`Mô tả không được vượt quá ${MAX_SEARCH_TEXT_LENGTH} ký tự.`);
  }
  const response = await request<SearchPageData, TextSearchRequest>({
    method: 'POST', url: '/search/text', params: { size: SEARCH_PAGE_SIZE }, data: { text: query },
    signal, timeout: SEARCH_TIMEOUT_MS,
  });
  return parseSearchPage(response.data);
}

export async function nextSearchPage(page: SearchPageData, signal: AbortSignal): Promise<SearchPageData> {
  const mode = page.searchType === 'IMAGE_ONLY' ? 'image' : page.searchType === 'TEXT_SEMANTIC' ? 'text' : null;
  if (!mode || !page.hasMore || !page.nextCursor) {
    throw new Error('Không thể tải trang tiếp theo của tìm kiếm này.');
  }
  const response = await request<SearchPageData>({
    method: 'GET', url: `/search/${mode}/${encodeURIComponent(page.searchId)}/results`,
    params: { cursor: page.nextCursor }, signal, timeout: SEARCH_TIMEOUT_MS,
  });
  return parseSearchPage(response.data);
}

/** Search with the indexed vector; external media does not need CORS download access. */
export async function searchSimilarImages(image: SearchImageData, signal: AbortSignal): Promise<SearchPageData> {
  const response = await request<SearchPageData>({
    method: 'POST', url: `/search/image/${encodeURIComponent(image.imageId)}/similar`,
    params: { size: SEARCH_PAGE_SIZE }, signal, timeout: SEARCH_TIMEOUT_MS,
  });
  return parseSearchPage(response.data);
}
