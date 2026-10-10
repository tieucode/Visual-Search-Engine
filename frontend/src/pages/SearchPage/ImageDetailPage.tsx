import { useCallback, useEffect, useState } from 'react';
import { ArrowLeft, Heart, ImageOff, Maximize2 } from 'lucide-react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { Button } from '../../components/common';
import { FeedStatus } from '../../components/search/FeedStatus';
import { MasonryGrid } from '../../components/search/MasonryGrid';
import { SearchImage } from '../../components/search/SearchImage';
import { ImageZoomModal } from '../../components/search/ImageZoomModal';
import { useSearchWorkspace } from '../../components/search/SearchWorkspace';
import { useSearchFeed } from '../../hooks/useSearchFeed';
import { findDemoImage, searchDemo } from '../../services/mockSearchService';
import { findRememberedImage } from '../../services/searchCache';
import { nextImagePage, searchSimilarImages } from '../../services/searchService';
import type { SearchImageData, SearchPageData } from '../../types/search';
import '../../styles/search.css';

export function ImageDetailPage() {
  const { imageId = '' } = useParams();
  // Remount per image: abort old requests and reset the thumbnail's fallback state.
  return <ImageDetail key={imageId} imageId={imageId} />;
}

function ImageDetail({ imageId }: { imageId: string }) {
  const location = useLocation();
  const navigate = useNavigate();
  const state = location.state as { image?: SearchImageData; from?: string } | null;
  const image = state?.image?.imageId === imageId ? state.image : findRememberedImage(imageId) || findDemoImage(imageId);
  const related = useSearchFeed();
  const workspace = useSearchWorkspace();
  const demo = imageId.startsWith('demo-');
  const liked = workspace.likes.has(imageId);
  const [zoomOpen, setZoomOpen] = useState(false);
  const closeZoom = useCallback(() => setZoomOpen(false), []);
  const close = () => {
    if (state?.from?.startsWith('/')) navigate(-1);
    else navigate(workspace.rootUrl.current || '/');
  };
  const startRelated = () => {
    if (!image) return;
    const excludeMain = (page: SearchPageData) => ({ ...page, items: page.items.filter(item => item.imageId !== imageId) });
    if (demo) {
      const mode = imageId.startsWith('demo-ocr') ? 'ocr' : 'text';
      void related.start(async signal => excludeMain(await searchDemo(mode, imageId, signal)), async (page, signal) => excludeMain(await searchDemo(mode, imageId, signal, page.nextCursor!)));
    } else {
      void related.start(async signal => excludeMain(await searchSimilarImages(image, signal)), async (page, signal) => excludeMain(await nextImagePage(page, signal)));
    }
  };
  useEffect(() => {
    window.scrollTo(0, 0);
    startRelated();
  }, []);
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (!zoomOpen && event.key === 'Escape' && !event.defaultPrevented && !(event.target as HTMLElement)?.closest('input, dialog, [role="dialog"], [role="menu"]')) close();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  if (!image) return <main className="search-page page-container page-container--grid"><div className="search-empty"><ImageOff size={32} aria-hidden="true" /><h1>Chưa thể mở ảnh này</h1><p>Hãy mở ảnh từ kết quả tìm kiếm để xem chi tiết.</p><Link to="/" className="button button--primary">Về trang tìm kiếm</Link></div></main>;

  return <main className="search-page search-detail page-container page-container--grid">
    <h1 className="sr-only">Xem ảnh</h1>
    <div className="search-detail__heading"><Button variant="ghost" onClick={close}><ArrowLeft size={18} aria-hidden="true" />Quay lại</Button></div>
    <section aria-label="Ảnh chính và hình ảnh tương tự">
      <MasonryGrid items={related.items} loading={related.loading} featured={
        <div className="image-detail-photo">
          <SearchImage image={image} main alt="Ảnh đang xem chi tiết" />
          <div className="image-detail-actions">
            <button type="button" className="image-detail-action" aria-label="Phóng to ảnh" title="Phóng to ảnh" aria-haspopup="dialog" onClick={() => setZoomOpen(true)}><Maximize2 size={23} aria-hidden="true" /></button>
            <button type="button" className={`image-detail-action image-detail-like ${liked ? 'is-liked' : ''}`} aria-label={liked ? 'Bỏ thích' : 'Thả tim'} title={liked ? 'Bỏ thích' : 'Thả tim'} aria-pressed={liked} onClick={() => workspace.toggleLike(imageId)}><Heart size={23} fill={liked ? 'currentColor' : 'none'} aria-hidden="true" /></button>
          </div>
        </div>
      } />
      <p className="sr-only" role="status">{related.loading ? 'Đang tìm các hình ảnh tương tự.' : `Đã tải ${related.items.length} hình ảnh tương tự.`}</p>
      {!related.loading && <FeedStatus feed={related} retry={startRelated} empty="Chưa có hình ảnh tương tự." />}
    </section>
    {zoomOpen && <ImageZoomModal image={image} onClose={closeZoom} />}
  </main>;
}
