import { useState } from 'react';
import { Button, Card, Form, Input, Typography, message } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { authApi } from '../../api/auth';
import { toErrorMessage } from '../../api/client';
import { useAppStore } from '../../store/appStore';

interface LoginValues {
  username: string;
  password: string;
}

/** PF-01 登录页：提交 /api/v1/auth/login，成功后保存 token 并跳转原目标页。 */
export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const setToken = useAppStore((state) => state.setToken);
  const setUsername = useAppStore((state) => state.setUsername);
  const setProfile = useAppStore((state) => state.setProfile);
  const [submitting, setSubmitting] = useState(false);

  const onFinish = async (values: LoginValues) => {
    setSubmitting(true);
    try {
      const result = await authApi.login(values.username, values.password);
      setToken(result.token);
      setUsername(result.user.username);
      setProfile(result.user);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from !== '/login' ? from : '/dashboard', { replace: true });
    } catch (error) {
      message.error(toErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg,#eff6ff,#eef2ff)',
      }}
    >
      <Card title="APS 排产系统 · 登录" style={{ width: 360 }}>
        <Form<LoginValues> layout="vertical" onFinish={onFinish} initialValues={{ username: 'admin' }}>
          <Form.Item label="账号" name="username" rules={[{ required: true, message: '请输入账号' }]}>
            <Input autoComplete="username" placeholder="username" />
          </Form.Item>
          <Form.Item label="密码" name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password autoComplete="current-password" placeholder="password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={submitting}>
            登录
          </Button>
          <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
            使用平台账号登录；会话为短时 JWT，登出或过期后需重新登录。
          </Typography.Paragraph>
        </Form>
      </Card>
    </div>
  );
}
