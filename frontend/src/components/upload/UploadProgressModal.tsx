import { UploadCloud } from 'lucide-react';
import { Modal } from '../common/Modal';
import { formatNumber } from '../../utils/format';

interface UploadProgressModalProps {
  done: number;
  total: number;
}

export function UploadProgressModal({ done, total }: UploadProgressModalProps) {
  const percent = total > 0 ? Math.round((done / total) * 100) : 0;

  return (
    <Modal label="Uploading" size="static">
      <div className="upload-progress" aria-live="polite">
        <span className="upload-progress__icon"><UploadCloud size={22} strokeWidth={1.75} /></span>
        <p className="upload-progress__count">{formatNumber(done)} / {formatNumber(total)}</p>
        <div className="progress progress--live" role="progressbar" aria-label="Uploading" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}>
          <div className="progress__fill" style={{ width: `${percent}%` }} />
        </div>
        <span className="upload-progress__percent">{percent}%</span>
      </div>
    </Modal>
  );
}
