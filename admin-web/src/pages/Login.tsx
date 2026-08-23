import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { App, Button, Card, Form, Input, Typography } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { api, getApiErrorDetail, getToken, setToken, setUser } from '../api/client';
import type { LoginResponse } from '../api/types';

interface LoginForm {
  username: string;
  password: string;
}

export default function Login() {
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [loading, setLoading] = useState(false);

  // 已登录则直接进入后台
  if (getToken()) {
    return <Navigate to="/" replace />;
  }

  const onFinish = async (values: LoginForm) => {
    setLoading(true);
    try {
      const data = await api.post<LoginResponse>('/auth/login', values);
      setToken(data.token);
      setUser(data.user);
      message.success(`欢迎回来，${data.user.username}`);
      navigate('/', { replace: true });
    } catch (e) {
      message.error(getApiErrorDetail(e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #1677ff 0%, #001529 100%)',
      }}
    >
      <Card style={{ width: 380, boxShadow: '0 8px 24px rgba(0,0,0,0.2)' }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <Typography.Title level={3} style={{ marginBottom: 4 }}>
            AI Social Agent
          </Typography.Title>
          <Typography.Text type="secondary">AI 自动回复助手 · 管理后台</Typography.Text>
        </div>
        <Form<LoginForm> name="login" size="large" onFinish={onFinish} initialValues={{ username: '', password: '' }}>
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} placeholder="用户名" autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="密码" autoComplete="current-password" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" block loading={loading}>
              登 录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
