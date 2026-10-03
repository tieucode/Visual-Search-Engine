import { LayoutGrid } from 'lucide-react';
import type { QueueItem } from '../../hooks/useImageQueue';
import { formatNumber } from '../../utils/format';
import { Thumb } from './Thumb';

const STRIP_LIMIT = 8;

interface PreviewStripProps {
  items: QueueItem[];
  onRemove: (id: number) => void;
  onZoom: (id: number) => void;
  onViewAll: () => void;
}

export function PreviewStrip({ items, onRemove, onZoom, onViewAll }: PreviewStripProps) {
  const recent = items.slice(-STRIP_LIMIT).reverse();

  return (
    <div className="upload-strip" role="list">
      {recent.map((item) => (
        <div key={item.id} role="listitem" className="upload-strip__item">
          <Thumb file={item.file} onOpen={() => onZoom(item.id)} onRemove={() => onRemove(item.id)} />
        </div>
      ))}
      <button type="button" className="strip-more" onClick={onViewAll} aria-label={`View all ${items.length} images`}>
        <LayoutGrid size={16} />
        <span>{formatNumber(items.length)}</span>
      </button>
    </div>
  );
}
