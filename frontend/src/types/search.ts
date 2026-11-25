export type SearchMode = 'image' | 'text' | 'ocr';
export type SearchType = 'IMAGE_ONLY' | 'TEXT_SEMANTIC' | 'TEXT_OCR' | 'RECOMMENDATION';
export type MediaKind = 'IMAGE' | 'GIF' | 'VIDEO';

export const MAX_SEARCH_TEXT_LENGTH = 500;

export interface TextSearchRequest {
  text: string;
}

export interface SearchImageData {
  imageId: string;
  imageUrl: string;
  thumbnailUrl: string;
  width: number | null;
  height: number | null;
  score: number;
  mediaKind?: MediaKind;
  durationMs?: number | null;
  matchedFrameMs?: number | null;
  /** Text recognized in the image, returned by OCR search. */
  rawTexts?: string[];
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
