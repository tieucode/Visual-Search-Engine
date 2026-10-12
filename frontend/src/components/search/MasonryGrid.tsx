import { useLayoutEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { ArrowUpRight } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import type { SearchImageData } from '../../types/search';
import { SearchImage } from './SearchImage';
import { useSearchWorkspace } from './SearchWorkspace';

/** Dimensions from the API reserve space before thumbnails load. Existing tiles do not move on append. */
export function MasonryGrid({ items, featured, loading = false }: { items: SearchImageData[]; featured?: ReactNode; loading?: boolean }) {
  const ref = useRef<HTMLDivElement>(null);
  const featureRef = useRef<HTMLDivElement>(null);
  const [featureHeight, setFeatureHeight] = useState(0);
  const [width, setWidth] = useState(0);
  const location = useLocation();
  const workspace = useSearchWorkspace();
  useLayoutEffect(() => {
    const node = ref.current;
    if (!node) return;
    setWidth(node.clientWidth);
    const observer = new ResizeObserver(entries => setWidth(entries[0].contentRect.width));
    observer.observe(node);
    return () => observer.disconnect();
  }, []);
  useLayoutEffect(() => {
    const node = featureRef.current;
    if (!node) return;
    setFeatureHeight(node.clientHeight);
    const observer = new ResizeObserver(entries => setFeatureHeight(entries[0].contentRect.height));
    observer.observe(node);
    return () => observer.disconnect();
  }, [Boolean(featured), width]);
  const layout = useMemo(() => {
    const gap = width < 640 ? 8 : 12;
    const columns = Math.max(2, Math.min(6, Math.floor((width + gap) / 280)));
    const tileWidth = (width - gap * (columns - 1)) / columns;
    const bottoms = Array<number>(columns).fill(0);
    const featureColumns = width < 640 ? columns : 2;
    const featureWidth = featureColumns * tileWidth + (featureColumns - 1) * gap;
    if (featured) for (let i = 0; i < featureColumns; i++) bottoms[i] = featureHeight + gap;
    const displayed = loading ? Array.from({ length: columns * 3 }, (_, i) => ({ width: 800, height: [1000, 700, 1150, 900][i % 4] })) : items;
    const tiles = displayed.map(item => {
      const column = bottoms.indexOf(Math.min(...bottoms));
      const ratio = item.width && item.height ? item.height / item.width : 1.25;
      const height = tileWidth * Math.min(2.2, Math.max(.6, ratio));
      const top = bottoms[column];
      bottoms[column] += height + gap;
      return { left: column * (tileWidth + gap), top, width: tileWidth, height };
    });
    return { tiles, height: Math.max(0, ...bottoms), featureWidth };
  }, [width, items, featured, featureHeight, loading]);

  return (
    <div ref={ref} className="search-masonry" style={{ height: layout.height }}>
      {featured && <div ref={featureRef} className="search-masonry__featured" style={{ width: Math.max(0, layout.featureWidth) }}>{featured}</div>}
      {width > 0 && loading && layout.tiles.map((tile, index) => <div className="search-tile skeleton" style={tile} key={index} aria-hidden="true" />)}
      {width > 0 && !loading && items.map((item, index) => (
        <Link
          key={item.imageId} to={`/images/${encodeURIComponent(item.imageId)}`}
          state={{ image: item, from: `${location.pathname}${location.search}` }}
          className="search-tile" style={layout.tiles[index]}
          aria-label={`Xem chi tiết ảnh ${index + 1}`}
          onClick={() => { if (location.pathname === '/') workspace.scrollY.current = window.scrollY; }}
        >
          <SearchImage image={item} alt={`Kết quả tìm kiếm ${index + 1}`} />
          <span className="search-tile__open" aria-hidden="true"><ArrowUpRight size={20} /></span>
        </Link>
      ))}
    </div>
  );
}
