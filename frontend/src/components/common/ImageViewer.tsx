import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { ChevronLeft, ChevronRight, ImageOff, Loader2, X } from 'lucide-react';
import { useImageZoom } from '../../hooks/useImageZoom';
import '../../styles/layout.css';

interface ImageViewerProps {
  src: string | null;
  alt: string;
  onClose: () => void;
  onPrevious?: () => void;
  onNext?: () => void;
  count?: string;
}

function ViewerImage({ src, alt, onClose }: { src: string; alt: string; onClose: () => void }) {
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const zoom = useImageZoom();
  return <div
    ref={zoom.viewportRef} className="image-viewer__stage"
    data-zoomed={zoom.zoomed} data-dragging={zoom.dragging}
    onPointerDown={zoom.onPointerDown} onPointerMove={zoom.onPointerMove}
    onPointerUp={zoom.onPointerUp} onPointerCancel={zoom.onPointerUp} onLostPointerCapture={zoom.onPointerUp}
    onClick={event => { if (!zoom.consumeDragClick() && event.target === event.currentTarget) onClose(); }}
    onDoubleClick={zoom.resetZoom}
  >
    {status === 'loading' && <span className="image-viewer__status" role="status" aria-label="Đang tải ảnh"><Loader2 size={24} aria-hidden="true" /></span>}
    {status === 'error' ? <p className="image-viewer__status" role="alert"><ImageOff size={24} aria-hidden="true" />Không thể tải ảnh</p> : <div className="image-viewer__frame" style={zoom.frameStyle}>
      <img
        className="image-viewer__image" src={src} alt={alt} draggable={false}
        style={zoom.imageStyle}
        onLoad={event => {
          zoom.setNaturalSize({ width: event.currentTarget.naturalWidth, height: event.currentTarget.naturalHeight });
          setStatus('ready');
        }}
        onError={() => setStatus('error')}
      />
    </div>}
  </div>;
}

/** Shared zoom viewer for remote search images and local upload previews. */
export function ImageViewer({ src, alt, onClose, onPrevious, onNext, count }: ImageViewerProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const helpId = useId();
  useEffect(() => {
    const dialog = dialogRef.current;
    const previousFocus = document.activeElement as HTMLElement | null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialog?.showModal();
    return () => {
      dialog?.close();
      document.body.style.overflow = previousOverflow;
      previousFocus?.focus({ preventScroll: true });
    };
  }, []);

  return createPortal(<dialog
    ref={dialogRef} className="image-viewer" aria-label="Ảnh phóng to" aria-describedby={helpId}
    onCancel={event => { event.preventDefault(); onClose(); }}
    onKeyDown={event => {
      if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); onClose(); }
      else if (event.key === 'ArrowLeft' && onPrevious) { event.preventDefault(); event.stopPropagation(); onPrevious(); }
      else if (event.key === 'ArrowRight' && onNext) { event.preventDefault(); event.stopPropagation(); onNext(); }
    }}
    onClick={event => { if (event.target === event.currentTarget) onClose(); }}
  >
    <p id={helpId} className="sr-only">Cuộn lên để phóng to nội dung trong khung ảnh, cuộn xuống để giảm độ phóng về mức ban đầu. Kéo ảnh đã phóng to để di chuyển. Dùng phím cộng, trừ để đổi độ phóng; phím 0 hoặc nhấn đúp ảnh để đặt lại.</p>
    <button type="button" className="image-viewer__control image-viewer__close" aria-label="Đóng ảnh phóng to" onClick={onClose} autoFocus><X size={24} aria-hidden="true" /></button>
    {onPrevious && <button type="button" className="image-viewer__control image-viewer__previous" aria-label="Ảnh trước" onClick={onPrevious}><ChevronLeft size={24} aria-hidden="true" /></button>}
    {onNext && <button type="button" className="image-viewer__control image-viewer__next" aria-label="Ảnh tiếp theo" onClick={onNext}><ChevronRight size={24} aria-hidden="true" /></button>}
    {src ? <ViewerImage key={src} src={src} alt={alt} onClose={onClose} /> : <span className="image-viewer__status" role="status" aria-label="Đang tải ảnh"><Loader2 size={24} aria-hidden="true" /></span>}
    {count && <span className="image-viewer__count" aria-live="polite">{count}</span>}
  </dialog>, document.body);
}
