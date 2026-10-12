
import { useEffect, useState } from "react";
import {
  getMyUploads,
  type UploadHistoryItem,
} from "../services/imageHistoryService";
import "../styles/history.css";

export function UploadHistoryPage() {
  const [images, setImages] = useState<UploadHistoryItem[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;

    async function loadUploads() {
      setLoading(true);
      setError("");

      try {
        const result = await getMyUploads(page, 20);

        if (!cancelled) {
          setImages(result.content);
          setTotalPages(result.totalPages);
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error
              ? err.message
              : "Đã xảy ra lỗi khi tải lịch sử upload."
          );
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    loadUploads();

    return () => {
      cancelled = true;
    };
  }, [page]);

  function formatFileSize(bytes: number | null) {
    if (bytes == null) return "Không rõ dung lượng";
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) {
      return `${(bytes / 1024).toFixed(1)} KB`;
    }

    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  }

  function formatDate(date: string) {
    return new Date(date).toLocaleString("vi-VN");
  }

  return (
    <main className="upload-history">
      <header className="upload-history__header">
        <div>
          <h1>Lịch sử upload</h1>
          <p>Quản lý những hình ảnh bạn đã tải lên.</p>
        </div>

        <span className="upload-history__count">
          {images.length} ảnh trên trang này
        </span>
      </header>

      {loading && (
        <div className="upload-history__state">
          Đang tải lịch sử upload...
        </div>
      )}

      {!loading && error && (
        <div className="upload-history__state upload-history__error">
          <p>{error}</p>
          <button onClick={() => setPage((current) => current)}>
            Thử lại
          </button>
        </div>
      )}

      {!loading && !error && images.length === 0 && (
        <div className="upload-history__empty">
          <h2>Chưa có hình ảnh nào</h2>
          <p>Những ảnh bạn tải lên sẽ xuất hiện tại đây.</p>
        </div>
      )}

      {!loading && !error && images.length > 0 && (
        <>
          <section className="upload-history__grid">
            {images.map((image) => (
              <article className="upload-card" key={image.id}>
                <a
                  className="upload-card__preview"
                  href={image.imageUrl}
                  target="_blank"
                  rel="noreferrer"
                  title="Mở ảnh gốc"
                >
                  <img
                    src={image.thumbnailUrl || image.imageUrl}
                    alt={`Ảnh ${image.id}`}
                    loading="lazy"
                  />
                </a>

                <div className="upload-card__body">
                  <span className="upload-card__format">
                    {image.fileFormat}
                  </span>

                  <p>
                    {image.width != null && image.height != null
                      ? `${image.width} × ${image.height} px`
                      : "Chưa có thông tin kích thước"}
                  </p>

                  <p>{formatFileSize(image.fileSize)}</p>

                  <time dateTime={image.createdAt}>
                    {formatDate(image.createdAt)}
                  </time>
                </div>
              </article>
            ))}
          </section>

          <nav className="upload-history__pagination">
            <button
              disabled={page === 0}
              onClick={() => setPage((current) => current - 1)}
            >
              ← Trang trước
            </button>

            <span>
              Trang {page + 1} / {totalPages}
            </span>

            <button
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((current) => current + 1)}
            >
              Trang sau →
            </button>
          </nav>
        </>
      )}
    </main>
  );
}
