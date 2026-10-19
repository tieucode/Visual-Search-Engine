import type { SearchImageData } from '../types/search';
import { isSearchImage } from './searchResponse';

const CACHE_KEY = 'visearch.searchImages';

function readImages(): SearchImageData[] {
  try {
    const value: unknown = JSON.parse(sessionStorage.getItem(CACHE_KEY) || '[]');
    return Array.isArray(value) ? value.filter(isSearchImage) : [];
  } catch { return []; }
}

export function rememberImages(images: SearchImageData[]): void {
  try {
    const unique = new Map(readImages().map(image => [image.imageId, image]));
    for (const image of images) {
      unique.delete(image.imageId);
      unique.set(image.imageId, image);
    }
    sessionStorage.setItem(CACHE_KEY, JSON.stringify([...unique.values()].slice(-200)));
  } catch { /* Navigation state remains available if storage is full or disabled. */ }
}

export function findRememberedImage(imageId: string): SearchImageData | undefined {
  return readImages().find(image => image.imageId === imageId);
}
