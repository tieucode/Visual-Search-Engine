import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ArrowLeft, Heart, ImageOff, Maximize2 } from 'lucide-react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { ImageViewer } from '../../components/common/ImageViewer';
import { Button } from '../../components/common';
import { FeedStatus } from '../../components/search/FeedStatus';
import { MasonryGrid } from '../../components/search/MasonryGrid';
import { SearchImage } from '../../components/search/SearchImage';
import { useSearchWorkspace } from '../../components/search/SearchWorkspace';
import { useSearchFeed } from '../../hooks/useSearchFeed';
import { useMediaQuery } from '../../hooks/useMediaQuery';
import { findDemoImage, searchDemo } from '../../services/mockSearchService';
import { findRememberedImage } from '../../services/searchCache';
import { nextSearchPage, searchSimilarImages } from '../../services/searchService';
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
  const image = useMemo(() => state?.image?.imageId === imageId ? state.image : findRememberedImage(imageId) || findDemoImage(imageId), [state?.image, imageId]);
  const related = useSearchFeed();
  const workspace = useSearchWorkspace();
  const demo = imageId.startsWith('demo-');
  const liked = workspace.likes.has(imageId);
  const [zoomStartMs, setZoomStartMs] = useState<number | null>(null);
  const zoomOpen = zoomStartMs !== null;
  const videoRef = useRef<HTMLVideoElement>(null);
  const resumeInline = useRef(false);
  const [videoFailed, setVideoFailed] = useState(false);
  const reduceMotion = useMediaQuery('(prefers-reduced-motion: reduce)');
  const closeZoom = useCallback(() => {
    setZoomStartMs(null);
    if (resumeInline.current) void videoRef.current?.play().catch(() => undefined);
    resumeInline.current = false;
  }, []);
  const close = () => {
    if (state?.from?.startsWith('/')) navigate(-1);
    else navigate(workspace.rootUrl.current || '/');
  };
  const startRelated = useCallback(() => {
    if (!image) return;
    const excludeMain = (page: SearchPageData) => ({ ...page, items: page.items.filter(item => item.imageId !== imageId) });
    if (demo) {
      const mode = imageId.startsWith('demo-ocr') ? 'ocr' : 'text';
      void related.start(async signal => excludeMain(await searchDemo(mode, imageId, signal)), async (page, signal) => excludeMain(await searchDemo(mode, imageId, signal, page.nextCursor!)));
    } else {
      void related.start(async signal => excludeMain(await searchSimilarImages(image, signal)), async (page, signal) => excludeMain(await nextSearchPage(page, signal)));
    }
  }, [image, imageId, demo, related.start]);
  useEffect(() => {
    window.scrollTo(0, 0);
    const timer = window.setTimeout(startRelated, 0);
    return () => window.clearTimeout(timer);
  }, [startRelated]);
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (!zoomOpen && event.key === 'Escape' && !event.defaultPrevented && !(event.target as HTMLElement)?.closest('input, dialog, [role="dialog"], [role="menu"]')) close();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  if (!image) return <main className="search-page page-container page-container--grid"><div className="search-empty"><ImageOff size={32} aria-hidden="true" /><h1>Chưa thể mở ảnh này</h1><p>Hãy mở ảnh từ kết quả tìm kiếm để xem chi tiết.</p><Link to="/" className="button button--primary">Về trang tìm kiếm</Link></div></main>;

  const clip = image.mediaKind === 'GIF' || image.mediaKind === 'VIDEO';
  const showVideo = image.mediaKind === 'VIDEO' && !videoFailed;
  const animateGif = image.mediaKind === 'GIF' && !reduceMotion && !zoomOpen;
  const zoomLabel = clip ? 'Phóng to clip' : 'Phóng to ảnh';
  const openZoom = () => {
    const video = videoRef.current;
    setZoomStartMs(video && video.readyState > 0 && !video.ended ? video.currentTime * 1000 : image.matchedFrameMs ?? 0);
    resumeInline.current = !!video && !video.paused;
    video?.pause();
  };
  return <main className="search-page search-detail page-container page-container--grid">
    <h1 className="sr-only">{clip ? 'Xem clip' : 'Xem ảnh'}</h1>
    <div className="search-detail__heading"><Button variant="ghost" onClick={close}><ArrowLeft size={18} aria-hidden="true" />Quay lại</Button></div>
    <section aria-label="Nội dung chính và kết quả tương tự">
      <MasonryGrid items={related.items} loading={related.loading} featured={
        <div className="image-detail-photo" data-video={showVideo}>
          {showVideo ? <video
            ref={videoRef}
            className="image-detail-clip" src={image.imageUrl} poster={image.thumbnailUrl}
            width={image.width || undefined} height={image.height || undefined}
            aria-label="Video đang xem chi tiết" controls autoPlay={!reduceMotion} muted playsInline
            onLoadedMetadata={event => { event.currentTarget.currentTime = (image.matchedFrameMs ?? 0) / 1000; }}
            onError={() => setVideoFailed(true)}
          /> : <SearchImage image={image} main animateGif={animateGif}
            alt={animateGif ? 'GIF đang xem chi tiết' : clip ? 'Khung hình đại diện' : 'Ảnh đang xem chi tiết'} />}
          <div className="image-detail-actions">
            <button type="button" className="image-detail-action" aria-label={zoomLabel} title={zoomLabel} aria-haspopup="dialog" onClick={openZoom}><Maximize2 size={23} aria-hidden="true" /></button>
            <button type="button" className={`image-detail-action image-detail-like ${liked ? 'is-liked' : ''}`} aria-label={liked ? 'Bỏ thích' : 'Thả tim'} title={liked ? 'Bỏ thích' : 'Thả tim'} aria-pressed={liked} onClick={() => workspace.toggleLike(imageId)}><Heart size={23} fill={liked ? 'currentColor' : 'none'} aria-hidden="true" /></button>
          </div>
        </div>
      } />
      <p className="sr-only" role="status">{related.loading ? 'Đang tìm nội dung tương tự.' : `Đã tải ${related.items.length} kết quả tương tự.`}</p>
      {!related.loading && <FeedStatus feed={related} retry={startRelated} empty="Chưa có kết quả tương tự." />}
    </section>
    {zoomOpen && <ImageViewer
      src={image.imageUrl} alt={clip ? 'Clip đang xem chi tiết' : 'Ảnh đang xem chi tiết'}
      video={image.mediaKind === 'VIDEO'} videoStartMs={image.mediaKind === 'VIDEO' ? zoomStartMs : undefined}
      onClose={closeZoom}
    />}
  </main>;
}
