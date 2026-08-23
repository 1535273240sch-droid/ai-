import { useMemo } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { App, Avatar, Button, Dropdown, Layout as AntLayout, Menu, Space, Typography } from 'antd';
import {
  DashboardOutlined,
  TeamOutlined,
  RobotOutlined,
  KeyOutlined,
  FileTextOutlined,
  LogoutOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { clearAuth, getUser } from '../api/client';

const { Header, Sider, Content } = AntLayout;

const MENU_ITEMS = [
  { key: '/', icon: <DashboardOutlined />, label: '仪表盘' },
  { key: '/contacts', icon: <TeamOutlined />, label: '联系人' },
  { key: '/personas', icon: <RobotOutlined />, label: '人设' },
  { key: '/licenses', icon: <KeyOutlined />, label: '卡密管理' },
  { key: '/logs', icon: <FileTextOutlined />, label: '日志' },
];

export default function Layout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { message, modal } = App.useApp();
  const user = getUser();

  const selectedKey = useMemo(() => {
    // F-3："/" 必须精确匹配，避免所有路径都高亮仪表盘
    const match = MENU_ITEMS.find((m) =>
      m.key === '/' ? location.pathname === '/' : location.pathname.startsWith(m.key),
    );
    return match ? match.key : '/';
  }, [location.pathname]);

  const handleLogout = () => {
    modal.confirm({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      okText: '退出',
      cancelText: '取消',
      onOk: () => {
        clearAuth();
        message.success('已退出登录');
        navigate('/login', { replace: true });
      },
    });
  };

  return (
    <AntLayout style={{ minHeight: '100vh' }}>
      <Sider
        theme="light"
        breakpoint="lg"
        collapsedWidth={64}
        style={{ borderRight: '1px solid rgba(255,255,255,0.55)' }}
      >
        <div
          style={{
            height: 56,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#1d1d1f',
            fontWeight: 700,
            fontSize: 16,
            letterSpacing: 0.5,
            overflow: 'hidden',
            whiteSpace: 'nowrap',
          }}
        >
          AI Social Agent
        </div>
        <Menu
          theme="light"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={MENU_ITEMS}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <AntLayout>
        <Header
          style={{
            background: 'rgba(255,255,255,0.5)',
            padding: '0 24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
          }}
        >
          <Dropdown
            menu={{
              items: [{ key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: handleLogout }],
            }}
          >
            <Space style={{ cursor: 'pointer' }}>
              <Avatar size="small" icon={<UserOutlined />} />
              <Typography.Text strong>{user?.username ?? '管理员'}</Typography.Text>
            </Space>
          </Dropdown>
          <Button type="text" icon={<LogoutOutlined />} onClick={handleLogout} style={{ marginLeft: 8 }}>
            退出
          </Button>
        </Header>
        <Content style={{ margin: 24 }}>
          <Outlet />
        </Content>
      </AntLayout>
    </AntLayout>
  );
}
