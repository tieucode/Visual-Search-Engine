import { useEffect, useRef, useState, type PointerEvent } from 'react';

interface Dimensions { width: number; height: number }
interface Transform { scale: number; x: number; y: number }

const INITIAL_TRANSFORM: Transform = { scale: 1, x: 0, y: 0 };
const MIN_ZOOM = 1;
const MAX_ZOOM = 5;
const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

function constrain(transform: Transform, frame: Dimensions): Transform {
  const maxX = frame.width * (transform.scale - 1) / 2;
  const maxY = frame.height * (transform.scale - 1) / 2;
  return { ...transform, x: clamp(transform.x, -maxX, maxX), y: clamp(transform.y, -maxY, maxY) };
}

/** Fit a fixed frame to the viewport, then zoom and pan only its image content. */
export function useImageZoom() {
  const viewportRef = useRef<HTMLDivElement>(null);
  const [viewport, setViewport] = useState<Dimensions>({ width: 0, height: 0 });
  const [naturalSize, setNaturalSize] = useState<Dimensions>({ width: 0, height: 0 });
  const [transform, setTransform] = useState(INITIAL_TRANSFORM);
  const [dragging, setDragging] = useState(false);
  const drag = useRef<{ pointerId: number; x: number; y: number } | null>(null);
  const suppressClick = useRef(false);
  const fitScale = naturalSize.width && naturalSize.height
    ? Math.min(viewport.width / naturalSize.width, viewport.height / naturalSize.height) : 0;
  const width = naturalSize.width * fitScale;
  const height = naturalSize.height * fitScale;

  useEffect(() => {
    const element = viewportRef.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => {
      setViewport({ width: entry.contentRect.width, height: entry.contentRect.height });
      setTransform(INITIAL_TRANSFORM);
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const element = viewportRef.current;
    if (!element || !width || !height) return;
    const frame = { width, height };
    const zoom = (factor: number, x = 0, y = 0) => {
      setTransform(current => {
        const scale = clamp(current.scale * factor, MIN_ZOOM, MAX_ZOOM);
        const ratio = scale / current.scale;
        return constrain({ scale, x: x - (x - current.x) * ratio, y: y - (y - current.y) * ratio }, frame);
      });
    };
    const onWheel = (event: WheelEvent) => {
      event.preventDefault();
      const bounds = element.getBoundingClientRect();
      // Normalize line/page-based wheels as well as pixel-based trackpads.
      const unit = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? viewport.height : 1;
      const delta = clamp(event.deltaY * unit, -100, 100);
      zoom(Math.exp(-delta * 0.002), event.clientX - bounds.left - bounds.width / 2,
        event.clientY - bounds.top - bounds.height / 2);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.ctrlKey || event.metaKey || event.altKey) return;
      if (event.key === '+' || event.key === '=') zoom(1.2);
      else if (event.key === '-') zoom(1 / 1.2);
      else if (event.key === '0') setTransform(INITIAL_TRANSFORM);
      else return;
      event.preventDefault();
      event.stopPropagation();
    };
    const dialog = element.closest('dialog');
    // React's delegated wheel listener is passive; a native listener lets us prevent page scrolling.
    element.addEventListener('wheel', onWheel, { passive: false });
    dialog?.addEventListener('keydown', onKeyDown);
    return () => {
      element.removeEventListener('wheel', onWheel);
      dialog?.removeEventListener('keydown', onKeyDown);
    };
  }, [width, height, viewport]);

  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    suppressClick.current = false;
    if (transform.scale <= 1 || !event.isPrimary || event.button !== 0
        || !(event.target instanceof HTMLImageElement)) return;
    event.preventDefault();
    drag.current = { pointerId: event.pointerId, x: event.clientX, y: event.clientY };
    event.currentTarget.setPointerCapture(event.pointerId);
    suppressClick.current = true;
    setDragging(true);
  };

  const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    const previous = drag.current;
    if (!previous || previous.pointerId !== event.pointerId) return;
    const dx = event.clientX - previous.x;
    const dy = event.clientY - previous.y;
    drag.current = { pointerId: event.pointerId, x: event.clientX, y: event.clientY };
    setTransform(current => constrain({ ...current, x: current.x + dx, y: current.y + dy }, { width, height }));
  };

  const onPointerUp = (event: PointerEvent<HTMLDivElement>) => {
    if (drag.current?.pointerId !== event.pointerId) return;
    drag.current = null;
    setDragging(false);
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
  };

  return {
    viewportRef, setNaturalSize, dragging, zoomed: transform.scale > 1,
    frameStyle: {
      width, height, visibility: width && height ? 'visible' as const : 'hidden' as const,
    },
    imageStyle: {
      transform: `translate(${transform.x}px, ${transform.y}px) scale(${transform.scale})`,
    },
    resetZoom: () => setTransform(INITIAL_TRANSFORM),
    onPointerDown, onPointerMove, onPointerUp,
    consumeDragClick: () => {
      const suppressed = suppressClick.current;
      suppressClick.current = false;
      return suppressed;
    },
  };
}
