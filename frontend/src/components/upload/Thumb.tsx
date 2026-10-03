import { useEffect, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { getThumbnail } from '../../utils/thumbnail';

interface ThumbProps {
  file: File;
  fill?: boolean;
  onOpen: () => void;
  onRemove: () => void;
}

export function Thumb({ file, fill = false, onOpen, onRemove }: ThumbProps) {
  const ref = useRef<HTMLDivElement>(null);
  const [src, setSrc] = useState<string | null>(null);

  useEffect(() => {
    const node = ref.current;
    if (!node) return;
    let cancelled = false;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) return;
        observer.disconnect();
        getThumbnail(file).then((url) => { if (!cancelled) setSrc(url); }).catch(() => undefined);
      },
      { rootMargin: '200px' },
    );
    observer.observe(node);
    return () => {
      cancelled = true;
      observer.disconnect();
    };
  }, [file]);

  return (
    <div ref={ref} className={`thumb ${fill ? 'thumb--fill' : ''}`.trim()}>
      <button type="button" className="thumb__open" onClick={onOpen} aria-label={`Zoom ${file.name}`}>
        {src ? <img src={src} alt="" draggable={false} /> : <div className="thumb__skeleton skeleton" />}
      </button>
      <button type="button" className="thumb__remove" onClick={onRemove} aria-label={`Remove ${file.name}`}>
        <X size={12} strokeWidth={2.5} />
      </button>
    </div>
  );
}
