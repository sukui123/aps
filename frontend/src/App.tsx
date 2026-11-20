import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { BrowserRouter } from 'react-router-dom';
import { AppLayout } from './app/AppLayout';
import { AppRoutes } from './router';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 30_000 },
  },
});

/** 主题 token 对齐 docs/prototype/p0-prototype.html 与 styles/tokens.css。 */
const theme = {
  token: {
    colorPrimary: '#2563eb',
    colorSuccess: '#15803d',
    colorWarning: '#b45309',
    colorError: '#dc2626',
    borderRadius: 7,
    fontSize: 13,
  },
};

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider locale={zhCN} theme={theme}>
        <BrowserRouter>
          <AppLayout>
            <AppRoutes />
          </AppLayout>
        </BrowserRouter>
      </ConfigProvider>
    </QueryClientProvider>
  );
}
