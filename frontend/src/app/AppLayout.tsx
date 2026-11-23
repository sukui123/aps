import { useEffect } from 'react';
import { Button, Layout, Menu, Space, Tag } from 'antd';
import {
  AppstoreOutlined,
  BarChartOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  DatabaseOutlined,
  LogoutOutlined,
  OrderedListOutlined,
  ScheduleOutlined,
  TableOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { authApi } from '../api/auth';
import { useAppStore } from '../store/appStore';

const { Header, Sider, Content } = Layout;

/** 对齐技术方案 §6.2 的 9 个路由菜单；permission 为空表示所有登录用户可见。 */
const NAV_ITEMS = [
  { key: '/dashboard', icon: <AppstoreOutlined />, label: '工作台', permission: '' },
  { key: '/orders', icon: <OrderedListOutlined />, label: '订单池', permission: '' },
  { key: '/scheduling', icon: <ScheduleOutlined />, label: '排产任务', permission: '' },
  { key: '/plan', icon: <CalendarOutlined />, label: '计划甘特', permission: '' },
  { key: '/unscheduled', icon: <UnorderedListOutlined />, label: '未排清单', permission: '' },
  { key: '/publish', icon: <CheckCircleOutlined />, label: '发布审批', permission: '' },
  { key: '/master-data', icon: <DatabaseOutlined />, label: '主数据', permission: '' },
  { key: '/analysis', icon: <BarChartOutlined />, label: '负荷分析', permission: '' },
];

export function AppLayout({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const token = useAppStore((state) => state.token);
  const username = useAppStore((state) => state.username);
  const setProfile = useAppStore((state) => state.setProfile);
  const logout = useAppStore((state) => state.logout);
  const selected = location.pathname.startsWith('/orders') ? '/orders' : location.pathname;

  // /auth/me 驱动菜单/按钮级体验控制；服务端 403 才是权限判定的准绳
  const { data: profile } = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: authApi.me,
    enabled: Boolean(token),
  });

  useEffect(() => {
    if (profile) {
      setProfile(profile);
    }
  }, [profile, setProfile]);

  const visibleItems = NAV_ITEMS
    .filter((item) => !item.permission || (profile?.permissions ?? []).includes(item.permission))
    .map(({ key, icon, label }) => ({ key, icon, label }));

  const onLogout = async () => {
    try {
      await authApi.logout();
    } catch {
      // 即使服务端登出失败（如 token 已过期）也清理本地登录态
    }
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="dark" width={212}>
        <div
          style={{
            height: 56,
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            padding: '0 16px',
            color: '#fff',
            fontSize: 16,
            fontWeight: 600,
          }}
        >
          <span
            style={{
              width: 26,
              height: 26,
              borderRadius: 7,
              background: 'linear-gradient(135deg,#3b82f6,#6366f1)',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 12,
            }}
          >
            APS
          </span>
          排产系统
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selected]}
          items={visibleItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: '#fff',
            borderBottom: '1px solid var(--line)',
            padding: '0 24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <span style={{ color: 'var(--muted)' }}>
            <TableOutlined /> 有限产能 · 工序级排产
          </span>
          <Space size="small">
            <Tag color="geekblue">{profile?.roles?.[0] ?? 'P0 骨架'}</Tag>
            <span>{profile?.displayName ?? username ?? '未登录'}</span>
            <Button type="text" icon={<LogoutOutlined />} onClick={onLogout} title="登出" />
          </Space>
        </Header>
        <Content style={{ overflow: 'auto' }}>{children}</Content>
      </Layout>
    </Layout>
  );
}
