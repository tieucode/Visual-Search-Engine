import type { SearchImageData } from '../types/search';

const CACHE_KEY = 'visearch.searchImages';

export function rememberImages(images: SearchImageData[]): void {
  try {
    const previous: SearchImageData[] = JSON.parse(sessionStorage.getItem(CACHE_KEY) || '[]');
    const unique = new Map([...previous, ...images].map(image => [image.imageId, image]));
    sessionStorage.setItem(CACHE_KEY, JSON.stringify([...unique.values()].slice(-200)));
  } catch { /* Navigation state remains available if storage is full or disabled. */ }
}

export function findRememberedImage(imageId: string): SearchImageData | undefined {
  try {
    const images: SearchImageData[] = JSON.parse(sessionStorage.getItem(CACHE_KEY) || '[]');
    return images.find(image => image.imageId === imageId);
  } catch { return undefined; }
}
