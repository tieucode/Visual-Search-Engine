import { useEffect } from 'react';
import { FeedStatus } from '../../components/search/FeedStatus';
import { MasonryGrid } from '../../components/search/MasonryGrid';
import { useSearchWorkspace } from '../../components/search/SearchWorkspace';
import '../../styles/search.css';

export function SearchPage() {
  const { feed, mode, queryImage, preview, scrollY, retry } = useSearchWorkspace();
  useEffect(() => {
    const frame = requestAnimationFrame(() => window.scrollTo(0, scrollY.current));
    return () => cancelAnimationFrame(frame);
  }, []);
  const hasQueryImage = mode === 'image' && Boolean(preview && queryImage);

  return <main className={`search-page page-container page-container--grid ${hasQueryImage ? 'search-page--image' : ''}`}>
    <h1 className="sr-only">{mode === 'recommend' ? 'Ảnh dành cho bạn' : 'Kết quả tìm kiếm'}</h1>
    {hasQueryImage && queryImage && <img className="search-query-image" src={preview} width={queryImage.width} height={queryImage.height} alt="Ảnh tìm kiếm" />}
    <section className="search-results" aria-label={mode === 'recommend' ? 'Ảnh đề xuất mẫu' : 'Kết quả tìm kiếm'} aria-busy={feed.loading || feed.loadingMore}>
      <MasonryGrid items={feed.items} loading={feed.loading || !feed.page && !feed.error} />
      {!feed.loading && <FeedStatus feed={feed} retry={retry} />}
    </section>
  </main>;
}

