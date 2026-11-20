import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/** 用户/会话 UI 态（服务端数据由 TanStack Query 管理，技术方案 §1.2）。 */
interface AppState {
  token: string | null;
  username: string | null;
  setToken: (token: string | null) => void;
  setUsername: (username: string | null) => void;
  logout: () => void;
}

export const useAppStore = create<AppState>()(
  persist(
    (set) => ({
      token: null,
      username: null,
      setToken: (token) => set({ token }),
      setUsername: (username) => set({ username }),
      logout: () => set({ token: null, username: null }),
    }),
    { name: 'aps-auth' },
  ),
);
