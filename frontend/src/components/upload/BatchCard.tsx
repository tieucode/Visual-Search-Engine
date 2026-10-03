import type { BatchSummary } from '../../services/uploadService';
import { formatAgo, formatNumber } from '../../utils/format';

interface BatchCardProps {
  batch: BatchSummary;
  size?: 'compact' | 'comfortable';
}

export function BatchCard({ batch, size = 'compact' }: BatchCardProps) {
  const percent = batch.totalImages > 0 ? Math.min(100, Math.round((batch.processedImages / batch.totalImages) * 100)) : 0;

  return (
    <div className={`batch-card batch-card--${size}`}>
      <span className="batch-card__dot" aria-hidden="true" />
      <span className="batch-card__count">{formatNumber(batch.processedImages)} / {formatNumber(batch.totalImages)}</span>
      <div
        className="progress progress--thin progress--live"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
      >
        <div className="progress__fill" style={{ width: `${percent}%` }} />
      </div>
      <time className="batch-card__time" dateTime={batch.createdAt}>{formatAgo(batch.createdAt)}</time>
    </div>
  );
}
