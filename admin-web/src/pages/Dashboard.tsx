import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { App, Button, Card, Col, Empty, List, Row, Space, Statistic, Typography } from 'antd';
import {
  KeyOutlined,
  MessageOutlined,
  ReloadOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  SendOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { api, getApiErrorDetail } from '../api/client';
import type { DashboardStats } from '../api/types';

export default function Dashboard() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [loading, setLoading] = useState(false);

  const fetchStats = useCallback(async () => {
    setLoading(true);
    try {
      const data = await api.get<DashboardStats>('/dashboard/stats');
      setStats(data);
    } catch (e) {
      message.error(getApiErrorDetail(e));
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  const cards = [
    { title: '消息总量', value: stats?.message_count ?? 0, icon: <MessageOutlined />, color: '#1677ff' },
    { title: 'AI 回复量', value: stats?.reply_count ?? 0, icon: <SendOutlined />, color: '#52c41a' },
    { title: '活跃联系人', value: stats?.active_contacts ?? 0, icon: <TeamOutlined />, color: '#faad14' },
    { title: '人设数', value: stats?.persona_count ?? 0, icon: <RobotOutlined />, color: '#722ed1' },
    { title: '卡密总数', value: stats?.license_count ?? 0, icon: <KeyOutlined />, color: '#eb2f96' },
    { title: '有效卡密', value: stats?.active_license_count ?? 0, icon: <SafetyCertificateOutlined />, color: '#13c2c2' },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          仪表盘
        </Typography.Title>
        <Button icon={<ReloadOutlined />} onClick={fetchStats} loading={loading}>
          刷新
        </Button>
      </div>

      <Row gutter={[16, 16]}>
        {cards.map((c) => (
          <Col key={c.title} xs={24} sm={12} lg={8} xl={4}>
            <Card loading={loading && !stats}>
              <Statistic
                title={
                  <span>
                    <span style={{ color: c.color, marginRight: 6 }}>{c.icon}</span>
                    {c.title}
                  </span>
                }
                value={c.value}
              />
            </Card>
          </Col>
        ))}
      </Row>

      <Card
        title="最近日志"
        style={{ marginTop: 16 }}
        extra={
          <Typography.Link onClick={() => navigate('/logs')}>查看全部 →</Typography.Link>
        }
      >
        {stats && stats.recent_logs && stats.recent_logs.length > 0 ? (
          <List
            size="small"
            dataSource={stats.recent_logs}
            renderItem={(log) => (
              <List.Item>
                <List.Item.Meta
                  title={
                    <Space>
                      <Typography.Text strong>{log.event}</Typography.Text>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {log.created_at}
                      </Typography.Text>
                    </Space>
                  }
                  description={
                    log.payload ? (
                      <Typography.Text
                        code
                        style={{ fontSize: 12, display: 'block', maxWidth: 1200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                      >
                        {JSON.stringify(log.payload)}
                      </Typography.Text>
                    ) : null
                  }
                />
              </List.Item>
            )}
          />
        ) : (
          <Empty description="暂无日志" />
        )}
      </Card>
    </div>
  );
}
