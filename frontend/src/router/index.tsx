import { Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { AppLayout } from '../app/AppLayout';
import { RequireAuth } from '../shared/guards/RequireAuth';
import { LoginPage } from '../features/auth/LoginPage';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { OrdersPage } from '../features/orders/OrdersPage';
import { OrderDetailPage } from '../features/orders/OrderDetailPage';
import { SchedulingPage } from '../features/scheduling/SchedulingPage';
import { GanttPage } from '../features/gantt/GanttPage';
import { UnscheduledPage } from '../features/unscheduled/UnscheduledPage';
import { PublishPage } from '../features/publish/PublishPage';
import { MasterDataPage } from '../features/master-data/MasterDataPage';
import { AnalysisPage } from '../features/analysis/AnalysisPage';

/**
 * 路由表（技术方案 §6.2）：/login 公开；9 个业务页统一置于 RequireAuth + AppLayout 之下。
 * 原型内部用 gantt 命名，正式路由以 /plan 为准，保留兼容重定向。
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <AppLayout><Outlet /></AppLayout>
          </RequireAuth>
        }
      >
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/orders" element={<OrdersPage />} />
        <Route path="/orders/:id" element={<OrderDetailPage />} />
        <Route path="/scheduling" element={<SchedulingPage />} />
        <Route path="/plan" element={<GanttPage />} />
        <Route path="/gantt" element={<Navigate to="/plan" replace />} />
        <Route path="/unscheduled" element={<UnscheduledPage />} />
        <Route path="/publish" element={<PublishPage />} />
        <Route path="/master-data" element={<MasterDataPage />} />
        <Route path="/analysis" element={<AnalysisPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
