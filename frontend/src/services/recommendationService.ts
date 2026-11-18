import { request } from './apiClient';
import { parseSearchPage } from './searchResponse';
import type { SearchPageData } from '../types/search';

export async function getRecommendations(signal: AbortSignal): Promise<SearchPageData> {
  const response = await request<SearchPageData>({
    method: 'GET', url: '/recommendations', params: { size: 20 }, signal, timeout: 30_000,
  });
  return parseSearchPage(response.data);
}

export async function nextRecommendationPage(page: SearchPageData, signal: AbortSignal): Promise<SearchPageData> {
  const response = await request<SearchPageData>({
    method: 'GET', url: `/recommendations/${encodeURIComponent(page.searchId)}/results`,
    params: { cursor: page.nextCursor }, signal,
  });
  return parseSearchPage(response.data);
}

export async function recordRecommendationClick(imageId: string): Promise<void> {
  await request<null>({
    method: 'POST', url: `/recommendations/clicks/${encodeURIComponent(imageId)}`,
  });
}
