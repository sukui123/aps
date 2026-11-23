import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
} from 'axios';
import type { ApiResult } from './types';
import { useAppStore } from '../store/appStore';
import { uuidv4 } from '../shared/utils';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';

const http: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' },
});

/** 写请求默认带 Idempotency-Key；调用方显式传入时以调用方为准。 */
const WRITE_METHODS = new Set(['post', 'put', 'patch']);

http.interceptors.request.use((config) => {
  const token = useAppStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  if (WRITE_METHODS.has((config.method ?? 'get').toLowerCase())
      && !config.headers['Idempotency-Key']) {
    config.headers['Idempotency-Key'] = uuidv4();
  }
  return config;
});

/** 401/10002 统一清登录态并跳转登录页（PF-01 验收 #5）。 */
http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResult<unknown>>) => {
    const unauthorized = error.response?.status === 401
      || error.response?.data?.code === 10002;
    if (unauthorized) {
      useAppStore.getState().logout();
      if (!window.location.pathname.startsWith('/login')) {
        window.location.replace('/login');
      }
    }
    return Promise.reject(error);
  },
);

/** 统一解包 {code,message,data,traceId,timestamp}，code!==0 视为业务错误。 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiResult<T>>(config);
  const body = response.data;
  if (body.code !== 0) {
    throw new Error(`[${body.code}] ${body.message}`);
  }
  return body.data;
}

/** 网络/HTTP 层错误归一化，供 TanStack Query 展示。 */
export function toErrorMessage(error: unknown): string {
  const ax = error as AxiosError<ApiResult<unknown>>;
  if (ax.response?.data?.message) {
    return ax.response.data.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return '未知错误';
}

export default http;
