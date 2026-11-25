const THUMB_SIZE = 240;
const MAX_CONCURRENT = 3;

const cache = new Map<File, Promise<string>>();
const waiting: Array<() => void> = [];
let active = 0;

function acquire(): Promise<void> {
  if (active < MAX_CONCURRENT) {
    active += 1;
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    waiting.push(() => {
      active += 1;
      resolve();
    });
  });
}

function release(): void {
  active -= 1;
  waiting.shift()?.();
}

async function render(file: File): Promise<string> {
  if (file.type === 'video/mp4') return renderVideo(file);
  try {
    const bitmap = await createImageBitmap(file);
    try {
      return await renderFrame(bitmap, bitmap.width, bitmap.height);
    } finally {
      bitmap.close();
    }
  } catch {
    return URL.createObjectURL(file);
  }
}

async function renderVideo(file: File): Promise<string> {
  const source = URL.createObjectURL(file);
  const video = document.createElement('video');
  video.preload = 'auto';
  video.muted = true;
  let timeout: number | undefined;
  try {
    await new Promise<void>((resolve, reject) => {
      video.onloadeddata = () => resolve();
      video.onerror = () => reject(new Error('Cannot decode preview video'));
      timeout = window.setTimeout(() => reject(new Error('Video preview timed out')), 5000);
      video.src = source;
    });
    return await renderFrame(video, video.videoWidth, video.videoHeight);
  } finally {
    window.clearTimeout(timeout);
    video.onloadeddata = null;
    video.onerror = null;
    video.removeAttribute('src');
    video.load();
    URL.revokeObjectURL(source);
  }
}

async function renderFrame(frame: CanvasImageSource, width: number, height: number): Promise<string> {
  const scale = Math.min(1, THUMB_SIZE / Math.max(width, height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(width * scale));
  canvas.height = Math.max(1, Math.round(height * scale));
  const context = canvas.getContext('2d');
  if (!context) throw new Error('Preview canvas is unavailable');
  context.drawImage(frame, 0, 0, canvas.width, canvas.height);
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/webp', 0.8));
  if (!blob) throw new Error('Cannot encode preview');
  return URL.createObjectURL(blob);
}

/** Returns a small preview URL for the file. Results are cached until releaseThumbnail(). */
export function getThumbnail(file: File): Promise<string> {
  let pending = cache.get(file);
  if (!pending) {
    pending = acquire().then(() => render(file)).finally(release);
    cache.set(file, pending);
  }
  return pending;
}

export function releaseThumbnail(file: File): void {
  const pending = cache.get(file);
  if (!pending) return;
  cache.delete(file);
  pending.then((url) => URL.revokeObjectURL(url)).catch(() => undefined);
}
