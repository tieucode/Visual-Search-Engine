import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiClientError } from '../services/apiClient';
import { rememberImages } from '../services/searchCache';
import type { NextPageLoader, PageLoader, SearchImageData, SearchPageData } from '../types/search';

export function useSearchFeed() {
  const [items, setItems] = useState<SearchImageData[]>([]);
  const [page, setPage] = useState<SearchPageData | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [moreError, setMoreError] = useState<string | null>(null);
  const [unauthorized, setUnauthorized] = useState(false);
  const requestRef = useRef<AbortController | null>(null);
  const pageRef = useRef<SearchPageData | null>(null);
  const nextRef = useRef<NextPageLoader | null>(null);
  const busy = useRef(false);
  const generation = useRef(0);

  const reset = useCallback(() => {
    generation.current++;
    requestRef.current?.abort();
    pageRef.current = null;
    busy.current = false;
    setItems([]); setPage(null); setLoading(false); setLoadingMore(false);
    setError(null); setMoreError(null); setUnauthorized(false);
  }, []);

  useEffect(() => () => { generation.current++; requestRef.current?.abort(); }, []);

  const start = useCallback(async (first: PageLoader, next: NextPageLoader) => {
    reset();
    const current = generation.current;
    const controller = new AbortController();
    requestRef.current = controller;
    nextRef.current = next;
    busy.current = true;
    setLoading(true);
    try {
      const result = await first(controller.signal);
      if (current !== generation.current) return;
      pageRef.current = result;
      setPage(result);
      setItems([...new Map(result.items.map(item => [item.imageId, item])).values()]);
      rememberImages(result.items);
    } catch (failure) {
      if (controller.signal.aborted || current !== generation.current) return;
      setUnauthorized(failure instanceof ApiClientError && failure.status === 401);
      setError(failure instanceof Error ? failure.message : 'Không thể tải kết quả. Vui lòng thử lại.');
    } finally {
      if (current === generation.current) { busy.current = false; setLoading(false); }
    }
  }, [reset]);

  const loadMore = useCallback(async () => {
    const previous = pageRef.current;
    if (busy.current || !previous?.hasMore || !previous.nextCursor || !nextRef.current) return;
    busy.current = true;
    const current = generation.current;
    const controller = new AbortController();
    requestRef.current = controller;
    setLoadingMore(true); setMoreError(null);
    try {
      const result = await nextRef.current(previous, controller.signal);
      if (current !== generation.current) return;
      if (result.searchId !== previous.searchId || result.searchType !== previous.searchType
          || result.size !== previous.size
          || (result.hasMore && (!result.nextCursor || result.nextCursor === previous.nextCursor))) {
        throw new Error('Không thể tải trang tiếp theo. Vui lòng thử lại.');
      }
      pageRef.current = result;
      setPage(result);
      setItems(existing => [...new Map([...existing, ...result.items].map(item => [item.imageId, item])).values()]);
      rememberImages(result.items);
    } catch (failure) {
      if (controller.signal.aborted || current !== generation.current) return;
      setUnauthorized(failure instanceof ApiClientError && failure.status === 401);
      setMoreError(failure instanceof Error ? failure.message : 'Không thể tải thêm ảnh.');
    } finally {
      if (current === generation.current) { busy.current = false; setLoadingMore(false); }
    }
  }, []);

  return { items, page, loading, loadingMore, error, moreError, unauthorized, start, reset, loadMore };
}
