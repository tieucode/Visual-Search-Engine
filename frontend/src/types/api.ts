import type { AxiosRequestConfig } from 'axios';

/** Backend BaseResponse<T>; timestamp is an ISO UTC string on the wire. */
export interface BaseResponse<T> {
  status: number;
  message: string;
  timestamp: string;
  data: T;
}

/** Backend ApiErrorData, returned in BaseResponse.data on HTTP errors. */
export interface ApiErrorData {
  error: string;
  path: string;
  fieldErrors: Record<string, string> | null;
}

export type BaseRequest<TBody = unknown> = Omit<AxiosRequestConfig<TBody>, 'baseURL' | 'url' | 'data'> & {
  url: string;
  data?: TBody;
};
