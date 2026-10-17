import type { SearchImageData, SearchPageData } from '../types/search';

// Demo data remains for recommendations, OCR and related images opened from those feeds.
const photos = [
  ['photo-1441974231531-c6227db76b6e', 800, 1100],
  ['photo-1470770841072-f978cf4d019e', 900, 650],
  ['photo-1464822759023-fed622ff2c3b', 800, 1000],
  ['photo-1449844908441-8829872d2607', 800, 1100],
  ['photo-1472396961693-142e6e269027', 800, 950],
  ['photo-1470252649378-9c29740c9fa8', 900, 650],
  ['photo-1448375240586-882707db888b', 800, 1100],
  ['photo-1449158743715-0a90ebb6d2d8', 800, 1000],
  ['photo-1469474968028-56623f02e42e', 900, 650],
  ['photo-1500530855697-b586d89ba3ee', 800, 1000],
  ['photo-1501785888041-af3ef285b470', 900, 700],
  ['photo-1473448912268-2022ce9509d8', 800, 1100],
  ['photo-1511818966892-d7d671e672a2', 800, 1000],
  ['photo-1447752875215-b2761acb3c5d', 900, 700],
  ['photo-1518837695005-2083093ee35b', 800, 950],
  ['photo-1463936575829-25148e1db1b8', 800, 1100],
  ['photo-1487958449943-2429e8be8625', 800, 1000],
  ['photo-1426604966848-d7adac402bff', 900, 650],
  ['photo-1470071459604-3b5ec3a7fe05', 800, 1100],
  ['photo-1519681393784-d120267933ba', 800, 950],
  ['photo-1493246507139-91e8fad9978e', 900, 700],
  ['photo-1486718448742-163732cd1544', 800, 1100],
  ['photo-1433086966358-54859d0ed716', 800, 1000],
  ['photo-1497250681960-ef046c08a56e', 900, 700],
  ['photo-1476514525535-07fb3b4ae5f1', 800, 1100],
  ['photo-1501854140801-50d01698950b', 900, 700],
  ['photo-1484154218962-a197022b5858', 800, 1050],
  ['photo-1510798831971-661eb04b3739', 800, 1100],
  ['photo-1458668383970-8ddd3927deed', 900, 650],
  ['photo-1500534623283-312aade485b7', 800, 1050],
  ['photo-1445116572660-236099ec97a0', 800, 1100],
  ['photo-1441974231531-c6227db76b6e', 900, 700],
  ['photo-1482192596544-9eb780fc7f66', 800, 1000],
  ['photo-1472214103451-9374bd1c798e', 900, 650],
  ['photo-1501785888041-af3ef285b470', 800, 1050],
  ['photo-1475924156734-496f6cac6ec1', 900, 700],
] as const;

const photoImages: SearchImageData[] = photos.map(([photo, width, height], index) => ({
  imageId: `demo-text-${index}`, width, height, score: 0.96 - index * 0.009,
  imageUrl: `https://images.unsplash.com/${photo}?auto=format&fit=crop&w=${width}&h=${height}&q=85`,
  thumbnailUrl: `https://images.unsplash.com/${photo}?auto=format&fit=crop&w=480&h=${Math.round(height / width * 480)}&q=80`,
}));

const posterTitles = ['COFFEE', 'SLOW LIVING', 'WEEKEND', 'GOOD THINGS', 'THE DAILY', 'OPEN STUDIO', 'FRESH IDEAS', 'TAKE A BREAK', 'COFFEE CLUB', 'LESS IS MORE', 'SUNDAY MARKET', 'ART & DESIGN'];
const palettes = [['#e9e1d0', '#344537'], ['#dae4ee', '#23445d'], ['#eed2b8', '#733e2c'], ['#d9dfc5', '#384b31']];
const posterImages: SearchImageData[] = Array.from({ length: 36 }, (_, index) => {
  const [background, ink] = palettes[index % palettes.length];
  const height = [1000, 760, 1100, 900][index % 4];
  const title = posterTitles[index % posterTitles.length];
  const lines = title.split(' ');
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="800" height="${height}" viewBox="0 0 800 ${height}"><rect width="800" height="${height}" fill="${background}"/><rect x="30" y="30" width="740" height="${height - 60}" rx="2" fill="none" stroke="${ink}" stroke-width="2"/><text x="400" y="90" text-anchor="middle" fill="${ink}" font-family="Arial,sans-serif" font-size="18" letter-spacing="7">THE EVERYDAY COLLECTION</text><circle cx="400" cy="${height * .36}" r="${105 + index % 3 * 10}" fill="${ink}"/><path d="M320 ${height * .36} h160 m-80 -80 v160" stroke="${background}" stroke-width="8"/>${lines.map((line, i) => `<text x="400" y="${height * .65 + i * 60}" text-anchor="middle" fill="${ink}" font-family="Georgia,serif" font-size="68">${line.replace('&', '&amp;')}</text>`).join('')}<text x="400" y="${height - 68}" text-anchor="middle" fill="${ink}" font-family="Arial,sans-serif" font-size="17" letter-spacing="4">EST. 2024 · EVERY DAY, A NEW IDEA</text></svg>`;
  const imageUrl = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
  return { imageId: `demo-ocr-${index}`, imageUrl, thumbnailUrl: imageUrl, width: 800, height, score: .95 - index * .01 };
});

export function findDemoImage(id: string): SearchImageData | undefined {
  return [...photoImages, ...posterImages].find(image => image.imageId === id);
}

/** Replace this adapter with the personalized recommendation API when it is available. */
export async function recommendedDemo(signal: AbortSignal, cursor = '0'): Promise<SearchPageData> {
  await waitForDemo(signal);
  const offset = Number(cursor);
  const items = photoImages.slice(offset, offset + 20);
  const hasMore = offset + items.length < photoImages.length;
  return { searchId: 'demo-recommended', searchType: 'TEXT_SEMANTIC', items, size: 20, hasMore, nextCursor: hasMore ? String(offset + items.length) : null };
}

function waitForDemo(signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) { reject(new DOMException('Aborted', 'AbortError')); return; }
    const cancel = () => { clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')); };
    const timer = setTimeout(() => { signal.removeEventListener('abort', cancel); resolve(); }, 550);
    signal.addEventListener('abort', cancel, { once: true });
  });
}

export async function searchDemo(mode: 'text' | 'ocr', query: string, signal: AbortSignal, cursor = '0'): Promise<SearchPageData> {
  await waitForDemo(signal);
  const offset = Number(cursor);
  const source = mode === 'ocr' ? posterImages : photoImages;
  const shift = Array.from(query).reduce((sum, char) => sum + char.charCodeAt(0), 0) % source.length;
  const ordered = [...source.slice(shift), ...source.slice(0, shift)];
  const items = ordered.slice(offset, offset + 12);
  const hasMore = offset + items.length < ordered.length;
  return {
    searchId: `demo-${mode}-${query}`, searchType: mode === 'text' ? 'TEXT_SEMANTIC' : 'TEXT_OCR',
    items, size: 12, hasMore, nextCursor: hasMore ? String(offset + items.length) : null,
  };
}
