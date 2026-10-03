import { request } from './apiClient';

/** Số ảnh tối đa mỗi request upload (khớp backend). */
export const CHUNK_SIZE = 50;
export const COMPACT_BATCH_SIZE = 10;

export interface ChunkResult {
  batchId: string;
  uploadedCount: number;
  failedCount: number;
}

export interface BatchSummary {
  batchId: string;
  processedImages: number;
  totalImages: number;
  status: string;
  createdAt: string;
}

export interface PagedResult<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
}

export async function initBatch(totalImages: number): Promise<{ batchId: string }> {
  const response = await request<{ batchId: string }, { totalImages: number }>({
    method: 'POST',
    url: '/uploads/batches/init',
    data: { totalImages },
  });
  return response.data;
}

export async function uploadChunk(batchId: string, files: File[], isLast: boolean): Promise<ChunkResult> {
  const form = new FormData();
  files.forEach((file) => form.append('files', file));
  const response = await request<ChunkResult, FormData>({
    method: 'POST',
    url: `/uploads/batches/${batchId}/images`,
    params: { isLast },
    data: form,
    timeout: 120_000,
  });
  return response.data;
}

export async function listBatches(page: number, size: number, signal?: AbortSignal): Promise<PagedResult<BatchSummary>> {
  const response = await request<PagedResult<BatchSummary>>({
    method: 'GET',
    url: '/uploads/batches',
    params: { status: 'PROCESSING', page, size, sort: 'createdAt,desc' },
    signal,
  });
  return response.data;
}
