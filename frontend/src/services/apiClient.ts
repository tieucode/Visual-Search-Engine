import axios, { AxiosError } from 'axios';
import { clearAccessToken, getAccessToken } from './authToken';
import type { ApiErrorData, BaseRequest, BaseResponse } from '../types/api';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  timeout: 15_000,
});

apiClient.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token && !/^\/?auth\//.test(config.url ?? '')) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

function isApiErrorData(value: unknown): value is ApiErrorData {
  return typeof value === 'object' && value !== null && 'error' in value && typeof value.error === 'string';
}

function isBaseResponse(value: unknown): value is BaseResponse<unknown> {
  return typeof value === 'object' && value !== null &&
    'status' in value && typeof value.status === 'number' &&
    'message' in value && typeof value.message === 'string' &&
    'timestamp' in value && typeof value.timestamp === 'string' &&
    'data' in value;
}

export class ApiClientError extends Error {
  constructor(
    message: string,
    public readonly status: number | null,
    public readonly details: ApiErrorData | null,
    options?: ErrorOptions,
  ) {
    super(message, options);
    this.name = 'ApiClientError';
  }

  get fieldErrors(): Record<string, string> {
    return this.details?.fieldErrors ?? {};
  }
}

/** Returns the full backend envelope. Use response.data for the endpoint payload. */
export async function request<TResponse, TBody = unknown>(
  config: BaseRequest<TBody>,
): Promise<BaseResponse<TResponse>> {
  if (!config.url.startsWith('/') || config.url.startsWith('//')) {
    throw new ApiClientError('Đường dẫn API phải bắt đầu bằng /', null, null);
  }
  try {
    const response = await apiClient.request<unknown>(config);
    if (!isBaseResponse(response.data)) {
      throw new ApiClientError('Phản hồi API không đúng định dạng', response.status, null);
    }
    return response.data as BaseResponse<TResponse>;
  } catch (error) {
    if (error instanceof ApiClientError) throw error;
    if (error instanceof AxiosError) {
      const body: unknown = error.response?.data;
      const envelope = isBaseResponse(body) ? body : null;
      const status = error.response?.status ?? null;
      if (status === 401 && !/^\/?auth\//.test(error.config?.url ?? '')) clearAccessToken();
      throw new ApiClientError(
        envelope?.message || (status === null ? 'Không thể kết nối tới máy chủ' : 'Yêu cầu thất bại'),
        status,
        isApiErrorData(envelope?.data) ? envelope.data : null,
        { cause: error },
      );
    }
    throw error;
  }
}
