import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { ChevronLeft, ChevronRight, X } from 'lucide-react';
import type { QueueItem } from '../../hooks/useImageQueue';
import '../../styles/layout.css';

interface LightboxProps {
  /** Items in display order (newest first). */
  items: QueueItem[];
  currentId: number;
  onChange: (id: number) => void;
  onClose: () => void;
}

export function Lightbox({ items, currentId, onChange, onClose }: LightboxProps) {
  const index = items.findIndex((item) => item.id === currentId);
  const current = items[index];
  const [src, setSrc] = useState<string | null>(null);

  useEffect(() => {
    if (!current) return;
    const url = URL.createObjectURL(current.file);
    setSrc(url);
    return () => URL.revokeObjectURL(url);
  }, [current]);

  useEffect(() => {
    if (!current) onClose();
  }, [current, onClose]);

  const go = (step: number) => {
    const next = items[(index + step + items.length) % items.length];
    if (next) onChange(next.id);
  };

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
      else if (event.key === 'ArrowLeft') go(-1);
      else if (event.key === 'ArrowRight') go(1);
      else return;
      event.preventDefault();
      event.stopPropagation(); // Keep Esc from also closing a modal underneath.
    };
    window.addEventListener('keydown', onKeyDown, true);
    return () => window.removeEventListener('keydown', onKeyDown, true);
  });

  if (!current) return null;

  return createPortal(
    <div
      className="modal-overlay lightbox"
      role="dialog"
      aria-modal="true"
      aria-label={current.file.name}
      onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}
    >
      <button type="button" className="lightbox__close icon-button" onClick={onClose} aria-label="Close"><X size={20} /></button>
      {items.length > 1 && (
        <>
          <button type="button" className="lightbox__nav lightbox__nav--prev" onClick={() => go(-1)} aria-label="Previous"><ChevronLeft size={22} /></button>
          <button type="button" className="lightbox__nav lightbox__nav--next" onClick={() => go(1)} aria-label="Next"><ChevronRight size={22} /></button>
        </>
      )}
      {src && <img key={current.id} className="lightbox__img" src={src} alt={current.file.name} draggable={false} />}
      <span className="lightbox__count">{index + 1} / {items.length}</span>
    </div>,
    document.body,
  );
}
