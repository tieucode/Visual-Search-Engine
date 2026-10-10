import { useState } from 'react';
import { ImageOff } from 'lucide-react';
import type { SearchImageData } from '../../types/search';

export function SearchImage({ image, main = false, alt }: { image: SearchImageData; main?: boolean; alt: string }) {
  const [fallback, setFallback] = useState(false);
  const [failed, setFailed] = useState(false);
  const [loaded, setLoaded] = useState(false);
  return (
    <div className={`search-image ${loaded || failed ? '' : 'skeleton'}`}>
      {failed ? <div className="search-image__unavailable"><ImageOff size={24} aria-hidden="true" /><span>Ảnh không còn khả dụng</span></div> : (
        <img
          src={main || fallback ? image.imageUrl : image.thumbnailUrl || image.imageUrl}
          alt={alt} width={image.width || undefined} height={image.height || undefined}
          loading={main ? 'eager' : 'lazy'} decoding="async"
          onLoad={() => setLoaded(true)}
          onError={() => { if (!main && !fallback && image.thumbnailUrl !== image.imageUrl) setFallback(true); else setFailed(true); }}
        />
      )}
    </div>
  );
}
