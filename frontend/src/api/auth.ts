import { request } from './client';
import type { LoginResponse, UserProfile } from './types';

/** PF-01 登录/登出/当前用户接口；BASE_URL 已含 /api/v1 前缀。 */
export const authApi = {
  login: (username: string, password: string) =>
    request<LoginResponse>({ url: '/auth/login', method: 'POST', data: { username, password } }),

  logout: () => request<void>({ url: '/auth/logout', method: 'POST' }),

  me: () => request<UserProfile>({ url: '/auth/me', method: 'GET' }),
};
