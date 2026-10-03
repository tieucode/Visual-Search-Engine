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
  const requestId = useRef(0);
  const inFlight = useRef(0);
  const unauthorizedRef = useRef(onUnauthorized);
  unauthorizedRef.current = onUnauthorized;

  const refresh = useCallback(async () => {
    const id = ++requestId.current;
    inFlight.current += 1;
    try {
      const data = await listBatches(0, COMPACT_BATCH_SIZE);
      if (id === requestId.current) {
        setBatches(data.content);
        setTotalElements(data.totalElements);
      }
    } catch (error) {
      if (error instanceof ApiClientError && error.status === 401) unauthorizedRef.current?.();
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
    return () => window.clearInterval(timer);
  }, [refresh]);

  return { batches, totalElements, loaded, refresh };
}
