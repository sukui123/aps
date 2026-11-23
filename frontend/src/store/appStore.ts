import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { UserProfile } from '../api/types';

/** 用户/会话 UI 态（服务端数据由 TanStack Query 管理，技术方案 §1.2）。 */
interface AppState {
  token: string | null;
  username: string | null;
  profile: UserProfile | null;
  setToken: (token: string | null) => void;
  setUsername: (username: string | null) => void;
  setProfile: (profile: UserProfile | null) => void;
  logout: () => void;
}

export const useAppStore = create<AppState>()(
  persist(
    (set) => ({
      token: null,
      username: null,
      profile: null,
      setToken: (token) => set({ token }),
      setUsername: (username) => set({ username }),
      setProfile: (profile) => set({ profile }),
      logout: () => set({ token: null, username: null, profile: null }),
    }),
    {
      name: 'aps-auth',
      // profile 由 /auth/me 实时刷新，不持久化，避免权限变更后前端仍用旧权限码
      partialize: (state) => ({ token: state.token, username: state.username }),
    },
  ),
);
