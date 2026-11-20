import { Layout, Menu, Tag } from 'antd';
import {
  AppstoreOutlined,
  BarChartOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  DatabaseOutlined,
  OrderedListOutlined,
  ScheduleOutlined,
  TableOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';

const { Header, Sider, Content } = Layout;

/** 对齐技术方案 §6.2 的 9 个路由菜单。 */
const NAV_ITEMS = [
  { key: '/dashboard', icon: <AppstoreOutlined />, label: '工作台' },
  { key: '/orders', icon: <OrderedListOutlined />, label: '订单池' },
  { key: '/scheduling', icon: <ScheduleOutlined />, label: '排产任务' },
  { key: '/plan', icon: <CalendarOutlined />, label: '计划甘特' },
  { key: '/unscheduled', icon: <UnorderedListOutlined />, label: '未排清单' },
  { key: '/publish', icon: <CheckCircleOutlined />, label: '发布审批' },
  { key: '/master-data', icon: <DatabaseOutlined />, label: '主数据' },
  { key: '/analysis', icon: <BarChartOutlined />, label: '负荷分析' },
];

export function AppLayout({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const selected =
    location.pathname.startsWith('/orders') ? '/orders' : location.pathname;

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
          items={NAV_ITEMS}
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
          <Tag color="geekblue">P0 骨架</Tag>
        </Header>
        <Content style={{ overflow: 'auto' }}>{children}</Content>
      </Layout>
    </Layout>
  );
}
