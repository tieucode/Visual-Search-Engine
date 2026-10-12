import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { ChevronLeft, ChevronRight, ImageOff, Loader2, X } from 'lucide-react';
import '../../styles/layout.css';

interface ImageViewerProps {
  src: string | null;
  alt: string;
  onClose: () => void;
  onPrevious?: () => void;
  onNext?: () => void;
  count?: string;
}

function ViewerImage({ src, alt }: { src: string; alt: string }) {
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  if (status === 'error') return <p className="image-viewer__status" role="alert"><ImageOff size={24} aria-hidden="true" />Không thể tải ảnh</p>;
  return <>
    {status === 'loading' && <span className="image-viewer__status" role="status" aria-label="Đang tải ảnh"><Loader2 size={24} aria-hidden="true" /></span>}
    <img className="image-viewer__image" src={src} alt={alt} draggable={false} onLoad={() => setStatus('ready')} onError={() => setStatus('error')} />
  </>;
}

/** Shared zoom viewer for remote search images and local upload previews. */
export function ImageViewer({ src, alt, onClose, onPrevious, onNext, count }: ImageViewerProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
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
    ref={dialogRef} className="image-viewer" aria-label="Ảnh phóng to"
    onCancel={event => { event.preventDefault(); onClose(); }}
    onKeyDown={event => {
      if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); onClose(); }
      else if (event.key === 'ArrowLeft' && onPrevious) { event.preventDefault(); event.stopPropagation(); onPrevious(); }
      else if (event.key === 'ArrowRight' && onNext) { event.preventDefault(); event.stopPropagation(); onNext(); }
    }}
    onClick={event => { if (event.target === event.currentTarget) onClose(); }}
  >
    <button type="button" className="image-viewer__control image-viewer__close" aria-label="Đóng ảnh phóng to" onClick={onClose} autoFocus><X size={24} aria-hidden="true" /></button>
    {onPrevious && <button type="button" className="image-viewer__control image-viewer__previous" aria-label="Ảnh trước" onClick={onPrevious}><ChevronLeft size={24} aria-hidden="true" /></button>}
    {onNext && <button type="button" className="image-viewer__control image-viewer__next" aria-label="Ảnh tiếp theo" onClick={onNext}><ChevronRight size={24} aria-hidden="true" /></button>}
    {src ? <ViewerImage key={src} src={src} alt={alt} /> : <span className="image-viewer__status" role="status" aria-label="Đang tải ảnh"><Loader2 size={24} aria-hidden="true" /></span>}
    {count && <span className="image-viewer__count" aria-live="polite">{count}</span>}
  </dialog>, document.body);
}
