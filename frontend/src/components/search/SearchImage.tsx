import { useState } from 'react';
import { ImageOff } from 'lucide-react';
import type { SearchImageData } from '../../types/search';

export function SearchImage({ image, main = false, animateGif = false, alt }: {
  image: SearchImageData;
  main?: boolean;
  animateGif?: boolean;
  alt: string;
}) {
  return <ImageResource key={`${main}:${animateGif}:${image.imageUrl}:${image.thumbnailUrl}`}
    image={image} main={main} animateGif={animateGif} alt={alt} />;
}

function ImageResource({ image, main, animateGif, alt }: {
  image: SearchImageData;
  main: boolean;
  animateGif: boolean;
  alt: string;
}) {
  const [fallback, setFallback] = useState(false);
  const [failed, setFailed] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [preview, setPreview] = useState(false);
  const [previewReady, setPreviewReady] = useState(false);
  const clip = image.mediaKind === 'VIDEO' || image.mediaKind === 'GIF';
  const animatedGif = animateGif && image.mediaKind === 'GIF';
  const thumbnail = image.thumbnailUrl || image.imageUrl;
  const primary = animatedGif || (main && !clip) ? image.imageUrl : thumbnail;
  const alternate = animatedGif ? thumbnail : !clip && !main ? image.imageUrl : primary;

  function stopPreview() {
    setPreview(false);
    setPreviewReady(false);
  }

  return (
    <div
      className={`search-image ${loaded || failed ? '' : 'skeleton'}`}
      onPointerEnter={event => {
        if (!main && clip && event.pointerType === 'mouse'
          && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) setPreview(true);
      }}
      onPointerLeave={stopPreview}
    >
      {failed ? <div className="search-image__unavailable"><ImageOff size={24} aria-hidden="true" /><span>{clip ? 'Không thể tải ảnh bìa clip' : 'Ảnh không còn khả dụng'}</span></div> : (
        <img
          src={fallback ? alternate : primary}
          alt={alt} width={image.width || undefined} height={image.height || undefined}
          loading={main ? 'eager' : 'lazy'} decoding="async"
          onLoad={() => setLoaded(true)}
          onError={() => { if (!fallback && alternate !== primary) setFallback(true); else setFailed(true); }}
        />
      )}
      {preview && (image.mediaKind === 'VIDEO' ? (
        <video
          className="search-image__preview" data-ready={previewReady}
          aria-hidden="true" src={image.imageUrl} autoPlay muted loop playsInline
          onLoadedMetadata={event => {
            if (image.matchedFrameMs) event.currentTarget.currentTime = image.matchedFrameMs / 1000;
          }}
          onPlaying={() => setPreviewReady(true)}
          onError={stopPreview}
        />
      ) : (
        <img
          className="search-image__preview" data-ready={previewReady}
          src={image.imageUrl} alt="" draggable={false}
          onLoad={() => setPreviewReady(true)}
          onError={stopPreview}
        />
      ))}
    </div>
  );
}
