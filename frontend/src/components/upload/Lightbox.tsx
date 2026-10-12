import { useEffect, useState } from 'react';
import type { QueueItem } from '../../hooks/useImageQueue';
import { ImageViewer } from '../common/ImageViewer';

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
  const [preview, setPreview] = useState<{ file: File; src: string } | null>(null);

  useEffect(() => {
    if (!current) return;
    const url = URL.createObjectURL(current.file);
    setPreview({ file: current.file, src: url });
    return () => URL.revokeObjectURL(url);
  }, [current?.file]);

  useEffect(() => {
    if (!current) onClose();
  }, [current, onClose]);

  const go = (step: number) => {
    const next = items[(index + step + items.length) % items.length];
    if (next) onChange(next.id);
  };

  if (!current) return null;

  return <ImageViewer
    src={preview?.file === current.file ? preview.src : null}
    alt={current.file.name}
    onClose={onClose}
    onPrevious={items.length > 1 ? () => go(-1) : undefined}
    onNext={items.length > 1 ? () => go(1) : undefined}
    count={items.length > 1 ? `${index + 1} / ${items.length}` : undefined}
  />;
}
