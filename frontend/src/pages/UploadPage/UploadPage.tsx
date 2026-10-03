import { useCallback, useEffect, useState } from 'react';
import { Activity, ChevronRight, UploadCloud } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { Button } from '../../components/common';
import { useToast } from '../../components/common/Toast';
import { BatchCard } from '../../components/upload/BatchCard';
import { DropZone } from '../../components/upload/DropZone';
import { ImagesModal } from '../../components/upload/ImagesModal';
import { Lightbox } from '../../components/upload/Lightbox';
import { PreviewStrip } from '../../components/upload/PreviewStrip';
import { UploadProgressModal } from '../../components/upload/UploadProgressModal';
import { MAX_FILE_BYTES, MAX_IMAGES, useImageQueue } from '../../hooks/useImageQueue';
import { useMediaQuery } from '../../hooks/useMediaQuery';
import { useProcessingBatches } from '../../hooks/useProcessingBatches';
import { ApiClientError } from '../../services/apiClient';
import { CHUNK_SIZE, initBatch, uploadChunk } from '../../services/uploadService';
import { formatNumber, shortName } from '../../utils/format';
import '../../styles/upload.css';

const DONE_DELAY_MS = 600;
const sleep = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms));

type Progress = { done: number; total: number } | null;

export function UploadPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const queue = useImageQueue();
  const isMobile = useMediaQuery('(max-width: 767px)');
  const { batches, loaded, refresh } = useProcessingBatches({
    onUnauthorized: () => navigate('/login', { replace: true }),
  });

  const [progress, setProgress] = useState<Progress>(null);
  const [showImages, setShowImages] = useState(false);
  const [zoomId, setZoomId] = useState<number | null>(null);
  const closeZoom = useCallback(() => setZoomId(null), []);

  const uploading = progress !== null;
  const batchLimit = isMobile ? 3 : 10;
  const maxMb = MAX_FILE_BYTES / (1024 * 1024);

  useEffect(() => {
    if (queue.items.length === 0) setShowImages(false);
  }, [queue.items.length]);

  useEffect(() => {
    if (!uploading) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [uploading]);

  const handleFiles = useCallback((files: File[]) => {
    const report = queue.add(files);
    if (report.unsupported.length > 0) {
      toast('error', report.unsupported.length === 1
        ? `${shortName(report.unsupported[0])} không hỗ trợ`
        : `${report.unsupported.length} tệp không hỗ trợ`);
    }
    if (report.oversized.length > 0) {
      toast('warning', report.oversized.length === 1
        ? `${shortName(report.oversized[0])} vượt ${maxMb}MB`
        : `${report.oversized.length} tệp vượt ${maxMb}MB`);
    }
    if (report.truncated > 0) toast('warning', `Đã đạt tối đa ${formatNumber(MAX_IMAGES)} ảnh`);
  }, [queue, toast, maxMb]);

  async function handleUpload() {
    const files = queue.items.map((item) => item.file);
    if (files.length === 0 || uploading) return;

    setProgress({ done: 0, total: files.length });
    try {
      const { batchId } = await initBatch(files.length);
      let done = 0;
      let failed = 0;
      for (let start = 0; start < files.length; start += CHUNK_SIZE) {
        const chunk = files.slice(start, start + CHUNK_SIZE);
        const result = await uploadChunk(batchId, chunk, start + CHUNK_SIZE >= files.length);
        failed += result.failedCount;
        done += chunk.length;
        setProgress({ done, total: files.length });
      }

      void refresh();
      await sleep(DONE_DELAY_MS);
      setProgress(null);
      queue.clear();
      toast('success', `${formatNumber(files.length - failed)} ảnh đã được tải lên`);
      if (failed > 0) toast('warning', `${formatNumber(failed)} ảnh lỗi`);
    } catch (error) {
      setProgress(null);
      if (error instanceof ApiClientError && error.status === 401) {
        navigate('/login', { replace: true });
        return;
      }
      toast('error', error instanceof ApiClientError ? error.message : 'Lỗi kết nối, thử lại');
    }
  }

  const visibleBatches = batches.slice(0, batchLimit);

  return (
    <main className="upload-page page-container page-container--grid">
      <h1 className="sr-only">Upload</h1>

      <section className="upload-main animate-enter" aria-label="Upload">
        <DropZone count={queue.items.length} totalBytes={queue.totalBytes} disabled={uploading} onFiles={handleFiles} />

        {queue.items.length > 0 && (
          <>
            <PreviewStrip items={queue.items} onRemove={queue.remove} onZoom={setZoomId} onViewAll={() => setShowImages(true)} />
            <Button size="large" className="upload-submit" onClick={handleUpload} disabled={uploading}>
              <UploadCloud size={18} aria-hidden="true" />
              Upload {formatNumber(queue.items.length)}
            </Button>
          </>
        )}
      </section>

      <aside className="upload-side animate-enter" aria-label="Index process">
        <div className="side-head">
          <h2 className="side-head__title">Index process</h2>
          <Link to="/batches" className="link-button" aria-label="Xem tất cả tiến trình">
            Xem tất cả
            <ChevronRight size={14} aria-hidden="true" />
          </Link>
        </div>

        <div className="batch-list">
          {!loaded && Array.from({ length: 2 }, (_, index) => <div key={index} className="batch-skeleton skeleton" />)}
          {loaded && visibleBatches.map((batch) => <BatchCard key={batch.batchId} batch={batch} />)}
          {loaded && batches.length === 0 && <div className="batch-empty"><Activity size={16} aria-hidden="true" />Idle</div>}
        </div>
      </aside>

      {showImages && (
        <ImagesModal
          items={queue.items}
          onRemove={queue.remove}
          onZoom={setZoomId}
          onClear={queue.clear}
          onClose={() => setShowImages(false)}
        />
      )}
      {zoomId !== null && (
        <Lightbox items={[...queue.items].reverse()} currentId={zoomId} onChange={setZoomId} onClose={closeZoom} />
      )}
      {progress && <UploadProgressModal done={progress.done} total={progress.total} />}
    </main>
  );
}
