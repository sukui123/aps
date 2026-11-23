import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAppStore } from '../../store/appStore';

/** 未登录访问受保护路由 → 重定向 /login，并记住原目标。 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const token = useAppStore((state) => state.token);
  const location = useLocation();
  if (!token) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  return <>{children}</>;
}
