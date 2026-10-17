import type { SearchImageData, SearchPageData } from '../types/search';

function isDimension(value: unknown): boolean {
  return value === null || (typeof value === 'number' && Number.isInteger(value) && value > 0);
}

export function isSearchImage(value: unknown): value is SearchImageData {
  if (typeof value !== 'object' || value === null) return false;
  const image = value as Partial<SearchImageData>;
  return typeof image.imageId === 'string' && image.imageId.length > 0 &&
    typeof image.imageUrl === 'string' && image.imageUrl.length > 0 &&
    typeof image.thumbnailUrl === 'string' &&
    isDimension(image.width) && isDimension(image.height) &&
    typeof image.score === 'number' && Number.isFinite(image.score);
}

/** Check the payload at the HTTP boundary before it reaches the feed or cache. */
export function parseSearchPage(value: unknown): SearchPageData {
  if (typeof value === 'object' && value !== null) {
    const page = value as Partial<SearchPageData>;
    if (typeof page.searchId === 'string' && page.searchId.length > 0 &&
        (page.searchType === 'IMAGE_ONLY' || page.searchType === 'TEXT_SEMANTIC') &&
        typeof page.size === 'number' && Number.isInteger(page.size) && page.size >= 1 && page.size <= 100 &&
        Array.isArray(page.items) && page.items.length <= page.size && page.items.every(isSearchImage) &&
        typeof page.hasMore === 'boolean' &&
        (page.hasMore ? typeof page.nextCursor === 'string' && page.nextCursor.length > 0 : page.nextCursor === null)) {
      return page as SearchPageData;
    }
  }
  throw new Error('Phản hồi tìm kiếm không đúng định dạng. Vui lòng thử lại.');
}
