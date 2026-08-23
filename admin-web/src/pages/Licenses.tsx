import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { KeyOutlined, StopOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { api, getApiErrorDetail } from '../api/client';
import type { LicenseItem, LicenseStatus } from '../api/types';

interface GenFormValues {
  count: number;
  days: number;
  features?: string;
}

const STATUS_MAP: Record<LicenseStatus, { color: string; label: string }> = {
  unused: { color: 'blue', label: '未使用' },
  active: { color: 'green', label: '已激活' },
  inactive: { color: 'default', label: '未激活' },
  revoked: { color: 'red', label: '已吊销' },
  expired: { color: 'orange', label: '已过期' },
};

function parseFeatures(raw?: string): Record<string, unknown> | null {
  if (!raw || !raw.trim()) return null;
  const features: Record<string, unknown> = {};
  raw
    .split(/[,，、]/)
    .map((s) => s.trim())
    .filter(Boolean)
    .forEach((f) => {
      features[f] = true;
    });
  return features;
}

export default function Licenses() {
  const { message } = App.useApp();
  const [genForm] = Form.useForm<GenFormValues>();
  const [items, setItems] = useState<LicenseItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [codes, setCodes] = useState<string[] | null>(null);

  const fetchItems = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<{ items: LicenseItem[] }>('/license/admin');
      setItems(res.items ?? []);
    } catch (e) {
      message.error(getApiErrorDetail(e));
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    fetchItems();
  }, [fetchItems]);

  const handleGenerate = async () => {
    let values: GenFormValues;
    try {
      values = await genForm.validateFields();
    } catch {
      return;
    }
    setGenerating(true);
    try {
      const features = parseFeatures(values.features);
      // F-1：features 为空时不发该字段，避免后端 422
      const body = {
        count: values.count,
        days: values.days,
        ...(features ? { features } : {}),
      };
      const res = await api.post<{ codes: string[] }>('/license/admin', body);
      setCodes(res.codes ?? []);
      message.success(`已生成 ${res.codes?.length ?? 0} 个卡密`);
      genForm.resetFields();
      fetchItems();
    } catch (e) {
      message.error(getApiErrorDetail(e));
    } finally {
      setGenerating(false);
    }
  };

  const handleRevoke = async (record: LicenseItem) => {
    try {
      await api.post<{ ok: true }>(`/license/admin/${record.code}/revoke`);
      message.warning(`已吊销 ${record.code}，该实例自动化已立即停止`);
      fetchItems();
    } catch (e) {
      message.error(getApiErrorDetail(e));
    }
  };

  return (
    <div>
      <Card title="批量生成卡密" style={{ marginBottom: 16 }}>
        <Form<GenFormValues> form={genForm} layout="inline" initialValues={{ count: 10, days: 30 }}>
          <Form.Item name="count" label="数量" rules={[{ required: true, message: '请输入数量' }]}>
            <InputNumber min={1} max={1000} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="days" label="有效天数" rules={[{ required: true, message: '请输入天数' }]}>
            <InputNumber min={1} max={3650} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="features" label="功能点（逗号分隔，可空）">
            <Input placeholder="例：auto_reply, half_mode" style={{ width: 260 }} />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              icon={<ThunderboltOutlined />}
              loading={generating}
              onClick={handleGenerate}
            >
              生成
            </Button>
          </Form.Item>
        </Form>
      </Card>

      <Card title="卡密列表">
        <Table<LicenseItem>
          rowKey="code"
          loading={loading}
          dataSource={items}
          pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
          scroll={{ x: 1100 }}
          columns={[
            { title: '卡密', dataIndex: 'code', width: 220, render: (v: string) => <Typography.Text code>{v}</Typography.Text> },
            {
              title: '状态',
              dataIndex: 'status',
              width: 100,
              render: (v: LicenseStatus) => <Tag color={STATUS_MAP[v]?.color ?? 'default'}>{STATUS_MAP[v]?.label ?? v}</Tag>,
            },
            { title: '激活人', dataIndex: 'activated_by', width: 140, render: (v?: string | null) => v || '—' },
            {
              title: '设备指纹',
              dataIndex: 'device_fingerprint',
              width: 160,
              ellipsis: true,
              render: (v?: string | null) => (v ? <Typography.Text type="secondary">{v}</Typography.Text> : '—'),
            },
            { title: '到期时间', dataIndex: 'expires_at', width: 180, render: (v: string) => v || '—' },
            {
              title: '功能点',
              dataIndex: 'features',
              width: 200,
              render: (v?: Record<string, unknown> | null) =>
                v && Object.keys(v).length > 0
                  ? Object.keys(v).map((f) => <Tag key={f}>{f}</Tag>)
                  : <Typography.Text type="secondary">—</Typography.Text>,
            },
            { title: '创建时间', dataIndex: 'created_at', width: 180 },
            {
              title: '操作',
              key: 'actions',
              width: 110,
              fixed: 'right',
              render: (_, record) => (
                <Popconfirm
                  title="吊销卡密"
                  description={`确定吊销 ${record.code} 吗？\n吊销后该卡密对应实例的所有自动化将立即停止。`}
                  okText="吊销"
                  cancelText="取消"
                  okButtonProps={{ danger: true }}
                  onConfirm={() => handleRevoke(record)}
                >
                  <Button
                    size="small"
                    danger
                    icon={<StopOutlined />}
                    disabled={record.status === 'revoked'}
                  >
                    吊销
                  </Button>
                </Popconfirm>
              ),
            },
          ]}
        />
      </Card>

      {/* 生成结果 */}
      <Modal
        title="生成成功"
        open={codes !== null}
        onCancel={() => setCodes(null)}
        footer={
          <Button type="primary" onClick={() => setCodes(null)}>
            完成
          </Button>
        }
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Text type="secondary">共生成 {codes?.length ?? 0} 个卡密：</Typography.Text>
          <div
            style={{
              maxHeight: 320,
              overflow: 'auto',
              background: '#f5f5f5',
              padding: 12,
              borderRadius: 6,
            }}
          >
            {codes?.map((c) => (
              <div key={c}>
                <KeyOutlined style={{ color: '#1677ff', marginRight: 8 }} />
                <Typography.Text code copyable>
                  {c}
                </Typography.Text>
              </div>
            ))}
          </div>
        </Space>
      </Modal>
    </div>
  );
}
