import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiClientError } from '../services/apiClient';
import { COMPACT_BATCH_SIZE, listBatches, type BatchSummary } from '../services/uploadService';

export const POLL_INTERVAL_MS = 1000;

interface Options {
  onUnauthorized?: () => void;
}

export function useProcessingBatches({ onUnauthorized }: Options = {}) {
  const [batches, setBatches] = useState<BatchSummary[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const requestId = useRef(0);
  const inFlight = useRef(0);
  const controllerRef = useRef<AbortController | null>(null);
  const unauthorizedRef = useRef(onUnauthorized);
  unauthorizedRef.current = onUnauthorized;

  const refresh = useCallback(async () => {
    const id = ++requestId.current;
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    inFlight.current += 1;
    try {
      const data = await listBatches(0, COMPACT_BATCH_SIZE, controller.signal);
      if (id === requestId.current) {
        setBatches(data.content);
        setTotalElements(data.totalElements);
        setError(null);
      }
    } catch (failure) {
      if (controller.signal.aborted || id !== requestId.current) return;
      if (failure instanceof ApiClientError && failure.status === 401) unauthorizedRef.current?.();
      setError(failure instanceof Error ? failure.message : 'Không thể tải tiến trình xử lý.');
    } finally {
      inFlight.current -= 1;
      if (id === requestId.current) setLoaded(true);
    }
  }, []);

  useEffect(() => {
    void refresh();
    const timer = window.setInterval(() => {
      if (!document.hidden && inFlight.current === 0) void refresh();
    }, POLL_INTERVAL_MS);
    return () => {
      window.clearInterval(timer);
      requestId.current += 1;
      controllerRef.current?.abort();
    };
  }, [refresh]);

  return { batches, totalElements, loaded, error, refresh };
}
