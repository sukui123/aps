import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
} from 'axios';
import type { ApiResult } from './types';
import { useAppStore } from '../store/appStore';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1';

const http: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 30_000,
  headers: { 'Content-Type': 'application/json' },
});

http.interceptors.request.use((config) => {
  const token = useAppStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

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
