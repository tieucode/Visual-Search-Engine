import { useCallback, useEffect, useRef, useState } from 'react';
import { releaseThumbnail } from '../utils/thumbnail';

export const MAX_IMAGES = 1000;
export const MAX_FILE_BYTES = 10 * 1024 * 1024;

const ALLOWED_TYPES = new Set(['image/jpeg', 'image/jpg', 'image/png', 'image/webp']);
const EXTENSION_TYPES: Record<string, string> = { jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', webp: 'image/webp' };

export interface QueueItem {
  id: number;
  file: File;
}

export interface AddReport {
  added: number;
  unsupported: string[];
  oversized: string[];
  duplicates: number;
  truncated: number;
}

let sequence = 0;

function isSupported(file: File): boolean {
  if (file.type) return ALLOWED_TYPES.has(file.type);
  const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
  return extension in EXTENSION_TYPES;
}

const fingerprint = (file: File) => `${file.name}:${file.size}`;

export function useImageQueue() {
  const [items, setItems] = useState<QueueItem[]>([]);
  const itemsRef = useRef<QueueItem[]>([]);

  const commit = useCallback((next: QueueItem[]) => {
    itemsRef.current = next;
    setItems(next);
  }, []);

  const add = useCallback((files: File[]): AddReport => {
    const report: AddReport = { added: 0, unsupported: [], oversized: [], duplicates: 0, truncated: 0 };
    const known = new Set(itemsRef.current.map((item) => fingerprint(item.file)));
    const room = MAX_IMAGES - itemsRef.current.length;
    const accepted: QueueItem[] = [];

    for (const file of files) {
      if (!isSupported(file)) { report.unsupported.push(file.name); continue; }
      if (file.size > MAX_FILE_BYTES) { report.oversized.push(file.name); continue; }
      const key = fingerprint(file);
      if (known.has(key)) { report.duplicates += 1; continue; }
      if (accepted.length >= room) { report.truncated += 1; continue; }
      known.add(key);
      accepted.push({ id: ++sequence, file });
    }

    if (accepted.length > 0) commit([...itemsRef.current, ...accepted]);
    report.added = accepted.length;
    return report;
  }, [commit]);

  const remove = useCallback((id: number) => {
    const target = itemsRef.current.find((item) => item.id === id);
    if (!target) return;
    releaseThumbnail(target.file);
    commit(itemsRef.current.filter((item) => item.id !== id));
  }, [commit]);

  const clear = useCallback(() => {
    itemsRef.current.forEach((item) => releaseThumbnail(item.file));
    commit([]);
  }, [commit]);

  const totalBytes = items.reduce((sum, item) => sum + item.file.size, 0);

  useEffect(() => () => itemsRef.current.forEach((item) => releaseThumbnail(item.file)), []);

  return { items, totalBytes, add, remove, clear };
}
