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
  try {
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, THUMB_SIZE / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    canvas.getContext('2d')?.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/webp', 0.8));
    if (blob) return URL.createObjectURL(blob);
  } catch {
    // Fall through to the original file below.
  }
  return URL.createObjectURL(file);
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
