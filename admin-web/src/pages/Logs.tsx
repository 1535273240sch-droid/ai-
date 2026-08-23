import { useCallback, useEffect, useState } from 'react';
import { App, Button, Card, Space, Table, Tag, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { api, getApiErrorDetail } from '../api/client';
import type { AuditLog } from '../api/types';

function prettyJson(payload: unknown): string {
  if (payload === undefined || payload === null) return '—';
  try {
    return JSON.stringify(payload, null, 2);
  } catch {
    return String(payload);
  }
}

export default function Logs() {
  const { message } = App.useApp();
  const [items, setItems] = useState<AuditLog[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const fetchItems = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<{ items: AuditLog[]; total: number }>(
        `/audit/logs?limit=${pageSize}&offset=${(page - 1) * pageSize}`,
      );
      setItems(res.items ?? []);
      setTotal(res.total ?? 0);
    } catch (e) {
      message.error(getApiErrorDetail(e));
    } finally {
      setLoading(false);
    }
  }, [message, page, pageSize]);

  useEffect(() => {
    fetchItems();
  }, [fetchItems]);

  return (
    <Card
      title="审计日志"
      extra={
        <Button icon={<ReloadOutlined />} onClick={fetchItems} loading={loading}>
          刷新
        </Button>
      }
    >
      <Table<AuditLog>
        rowKey="id"
        loading={loading}
        dataSource={items}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: [20, 50, 100],
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, ps) => {
            setPage(p);
            setPageSize(ps);
          },
        }}
        expandable={{
          expandedRowRender: (record) => (
            <pre style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all', fontSize: 12 }}>
              {prettyJson(record.payload)}
            </pre>
          ),
        }}
        columns={[
          { title: 'ID', dataIndex: 'id', width: 70 },
          {
            title: '事件',
            dataIndex: 'event',
            width: 220,
            render: (v: string) => <Tag>{v}</Tag>,
          },
          {
            title: '内容摘要',
            key: 'summary',
            render: (_, record) => (
              <Typography.Text
                type="secondary"
                style={{ fontSize: 12, display: 'block', maxWidth: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
              >
                {prettyJson(record.payload)}
              </Typography.Text>
            ),
          },
          { title: '时间', dataIndex: 'created_at', width: 200 },
        ]}
      />
      <Space style={{ marginTop: 8 }}>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          提示：点击行可展开完整 payload。
        </Typography.Text>
      </Space>
    </Card>
  );
}
