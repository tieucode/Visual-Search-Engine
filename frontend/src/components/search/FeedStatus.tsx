import { useEffect, useRef } from 'react';
import { AlertCircle, Check, Loader2, SearchX } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '../common';
import type { useSearchFeed } from '../../hooks/useSearchFeed';

export function FeedSkeleton() {
  return <div className="search-skeleton" role="status" aria-label="Đang tìm ảnh">
    {Array.from({ length: 12 }, (_, index) => <div className="skeleton" key={index} />)}
    <span className="sr-only">Đang tìm ảnh…</span>
  </div>;
}

export function FeedStatus({ feed, retry, empty = 'Không tìm thấy ảnh phù hợp.' }: {
  feed: ReturnType<typeof useSearchFeed>; retry: () => void; empty?: string;
}) {
  const sentinel = useRef<HTMLDivElement>(null);
  const { page, loading, loadingMore, error, moreError, unauthorized, loadMore } = feed;
  useEffect(() => {
    const node = sentinel.current;
    if (!node || !page?.hasMore || loading || loadingMore || error || moreError) return;
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) void loadMore();
    }, { rootMargin: '0px 0px 650px 0px' });
    observer.observe(node);
    return () => observer.disconnect();
  }, [page, loading, loadingMore, error, moreError, loadMore]);

  if (loading) return <FeedSkeleton />;
  if (unauthorized) return <div className="search-status" role="alert"><AlertCircle size={24} aria-hidden="true" /><p>Phiên đăng nhập đã hết hạn.</p><Link className="button button--primary" to="/login">Đăng nhập lại</Link></div>;
  if (error) return <div className="search-status" role="alert"><AlertCircle size={24} aria-hidden="true" /><p>{error}</p><Button variant="outline" onClick={retry}>Thử lại</Button></div>;
  if (!page) return null;
  if (!feed.items.length && !page.hasMore) return <div className="search-status" role="status"><SearchX size={28} aria-hidden="true" /><p>{empty}</p></div>;
  return <div ref={sentinel} className="search-feed-end" data-state={page.hasMore ? 'more' : 'end'}>
    {moreError ? <div role="alert"><p>{moreError}</p><Button variant="outline" onClick={() => void loadMore()}>Thử tải thêm</Button></div>
      : loadingMore ? <p role="status"><Loader2 className="search-spinner" size={18} aria-hidden="true" /><span className="sr-only">Đang tải thêm ảnh…</span></p>
      : page.hasMore ? <Button variant="ghost" onClick={() => void loadMore()}>Tải thêm ảnh</Button>
      : <p role="status"><Check size={16} aria-hidden="true" /><span className="sr-only">Bạn đã xem hết kết quả</span></p>}
  </div>;
}
