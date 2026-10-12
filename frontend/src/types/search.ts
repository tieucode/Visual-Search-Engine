export type SearchMode = 'image' | 'text' | 'ocr';
export type SearchType = 'IMAGE_ONLY' | 'TEXT_SEMANTIC' | 'TEXT_OCR';

export interface SearchImageData {
  imageId: string;
  imageUrl: string;
  thumbnailUrl: string;
  width: number | null;
  height: number | null;
  score: number;
}

export interface SearchPageData {
  searchId: string;
  searchType: SearchType;
  items: SearchImageData[];
  size: number;
  hasMore: boolean;
  nextCursor: string | null;
}

export type PageLoader = (signal: AbortSignal) => Promise<SearchPageData>;
export type NextPageLoader = (page: SearchPageData, signal: AbortSignal) => Promise<SearchPageData>;
