import { Button, Card, Form, Input, Typography } from 'antd';
import { PageShell } from '../../shared/components/PageShell';

/** 登录 PF-01/02：骨架占位（正式路由不暴露，供后续接入 /api/auth/login）。 */
export function LoginPage() {
  return (
    <PageShell title="登录" feature="PF-01/02" description="账号密码、JWT 会话、功能/数据权限（骨架）">
      <Card style={{ maxWidth: 360 }}>
        <Form layout="vertical">
          <Form.Item label="账号" name="username">
            <Input placeholder="username" />
          </Form.Item>
          <Form.Item label="密码" name="password">
            <Input.Password placeholder="password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>
            登录
          </Button>
          <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
            当前为骨架页，尚未接入认证接口。
          </Typography.Paragraph>
        </Form>
      </Card>
    </PageShell>
  );
}
