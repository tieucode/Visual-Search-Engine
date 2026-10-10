import { useCallback, useEffect, useRef, useState, type RefObject } from 'react';
import { createPortal } from 'react-dom';
import { ImageUp, Loader2, X } from 'lucide-react';
import { useSearchWorkspace } from './SearchWorkspace';

/** Nonblocking dialog below the header: the camera button can still toggle it. */
export function ImageSearchModal({ triggerRef }: { triggerRef: RefObject<HTMLButtonElement> }) {
  const { submitImage, setImageModalOpen } = useSearchWorkspace();
  const panelRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const generation = useRef(0);
  const busy = useRef(false);
  const [validating, setValidating] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [error, setError] = useState('');
  const submitRef = useRef(submitImage);
  submitRef.current = submitImage;

  const acceptFile = useCallback(async (file?: File) => {
    if (!file || busy.current) return;
    setError('');
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) { setError('Chọn ảnh JPG, PNG hoặc WebP.'); return; }
    if (file.size > 10 * 1024 * 1024) { setError('Ảnh tối đa 10 MB.'); return; }
    const current = ++generation.current;
    busy.current = true; setValidating(true);
    try {
      const bitmap = await createImageBitmap(file);
      const { width, height } = bitmap;
      bitmap.close();
      if (current !== generation.current) return;
      if (width > 10000 || height > 10000 || width * height > 40_000_000) { setError('Ảnh tối đa 40 triệu pixel, 10.000 px mỗi chiều.'); return; }
      submitRef.current({ file, width, height });
    } catch { if (current === generation.current) setError('Không thể đọc ảnh này. Chọn ảnh khác.'); }
    finally { if (current === generation.current) { busy.current = false; setValidating(false); } }
  }, []);

  useEffect(() => {
    panelRef.current?.focus();
    const close = () => setImageModalOpen(false);
    const outside = (event: PointerEvent) => {
      const target = event.target as Node;
      if (!panelRef.current?.contains(target) && !triggerRef.current?.contains(target)) close();
    };
    const keydown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { event.preventDefault(); event.stopImmediatePropagation(); close(); }
    };
    const paste = (event: ClipboardEvent) => {
      const file = event.clipboardData?.files[0];
      if (!file) return;
      event.preventDefault(); event.stopImmediatePropagation(); void acceptFile(file);
    };
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', keydown, true);
    document.addEventListener('paste', paste, true);
    return () => {
      generation.current++;
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('keydown', keydown, true);
      document.removeEventListener('paste', paste, true);
      if (panelRef.current?.contains(document.activeElement) || document.activeElement === document.body) triggerRef.current?.focus();
    };
  }, [acceptFile, setImageModalOpen, triggerRef]);

  return createPortal(<div ref={panelRef} id="image-search-dialog" className="image-search-dialog" role="dialog" aria-modal="false" aria-labelledby="image-search-title" tabIndex={-1}>
    <div className="image-search-dialog__heading"><h2 id="image-search-title">Tải ảnh để tìm kiếm</h2><button className="icon-button" type="button" aria-label="Đóng" onClick={() => setImageModalOpen(false)}><X size={20} aria-hidden="true" /></button></div>
    <button
      type="button" className="image-search-drop" data-dragging={dragging} disabled={validating} aria-describedby="image-search-help" aria-busy={validating}
      onClick={() => inputRef.current?.click()}
      onDragEnter={event => { event.preventDefault(); event.stopPropagation(); setDragging(true); }}
      onDragOver={event => { event.preventDefault(); event.stopPropagation(); }}
      onDragLeave={event => { event.stopPropagation(); if (!event.currentTarget.contains(event.relatedTarget as Node)) setDragging(false); }}
      onDrop={event => { event.preventDefault(); event.stopPropagation(); setDragging(false); void acceptFile(event.dataTransfer.files[0]); }}
    >
      {validating ? <Loader2 className="search-spinner" size={32} aria-hidden="true" /> : <ImageUp size={32} strokeWidth={1.5} aria-hidden="true" />}
      <span>{validating ? 'Đang đọc ảnh…' : 'Chọn ảnh hoặc kéo thả vào đây'}</span>
      <small id="image-search-help">JPG, PNG, WebP · 10 MB · Ctrl + V</small>
    </button>
    <input ref={inputRef} className="sr-only" type="file" accept="image/jpeg,image/png,image/webp" aria-label="Chọn ảnh tìm kiếm" tabIndex={-1} disabled={validating} onChange={event => { void acceptFile(event.target.files?.[0]); event.target.value = ''; }} />
    {error && <p className="field-error" role="alert">{error}</p>}
  </div>, document.body);
}
