import { Images, Trash2 } from 'lucide-react';
import { Modal } from '../common/Modal';
import type { QueueItem } from '../../hooks/useImageQueue';
import { formatNumber } from '../../utils/format';
import { Thumb } from './Thumb';

interface ImagesModalProps {
  items: QueueItem[];
  onRemove: (id: number) => void;
  onZoom: (id: number) => void;
  onClear: () => void;
  onClose: () => void;
}

export function ImagesModal({ items, onRemove, onZoom, onClear, onClose }: ImagesModalProps) {
  return (
    <Modal
      label="Selected images"
      onClose={onClose}
      title={<><Images size={16} aria-hidden="true" />{formatNumber(items.length)}</>}
      actions={
        <button type="button" className="icon-button icon-button--sm" onClick={onClear} aria-label="Clear all" title="Clear all">
          <Trash2 size={16} />
        </button>
      }
    >
      <div className="modal__body">
        <div className="thumb-grid">
          {[...items].reverse().map((item) => (
            <Thumb key={item.id} file={item.file} fill onOpen={() => onZoom(item.id)} onRemove={() => onRemove(item.id)} />
          ))}
        </div>
      </div>
    </Modal>
  );
}
