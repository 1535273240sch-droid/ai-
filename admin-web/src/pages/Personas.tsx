import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { api, getApiErrorDetail } from '../api/client';
import type { OkResponse, Persona } from '../api/types';

const { TextArea } = Input;

interface PersonaFormValues {
  name: string;
  config: string;
  is_default?: boolean;
  is_global?: boolean;
}

/** 安全地 JSON 序列化 config，用于展示/回填 */
function stringifyConfig(config: unknown): string {
  if (config === undefined || config === null) return '';
  try {
    return JSON.stringify(config, null, 2);
  } catch {
    return String(config);
  }
}

export default function Personas() {
  const { message } = App.useApp();
  const [form] = Form.useForm<PersonaFormValues>();
  const [items, setItems] = useState<Persona[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Persona | null>(null);
  const [saving, setSaving] = useState(false);

  const fetchItems = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<{ items: Persona[] }>('/personas');
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

  const openModal = (record?: Persona) => {
    setEditing(record ?? null);
    setOpen(true);
    if (record) {
      form.setFieldsValue({
        name: record.name,
        config: stringifyConfig(record.config),
        is_default: record.is_default,
        is_global: record.is_global,
      });
    } else {
      form.setFieldsValue({ name: '', config: '', is_default: false, is_global: false });
    }
  };

  const closeModal = () => {
    setOpen(false);
    setEditing(null);
    form.resetFields();
  };

  const handleSubmit = async () => {
    let values: PersonaFormValues;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    // 校验 config 为合法 JSON
    let config: unknown;
    try {
      config = values.config.trim() ? JSON.parse(values.config) : {};
    } catch {
      message.error('配置不是合法的 JSON，请检查格式');
      return;
    }

    setSaving(true);
    try {
      const body = {
        name: values.name.trim(),
        config,
        is_default: !!values.is_default,
        is_global: !!values.is_global,
      };
      if (editing) {
        await api.put<{ persona: Persona }>(`/personas/${editing.id}`, body);
        message.success('人设已更新');
      } else {
        await api.post<{ persona: Persona }>('/personas', body);
        message.success('人设已创建');
      }
      closeModal();
      fetchItems();
    } catch (e) {
      message.error(getApiErrorDetail(e));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (record: Persona) => {
    setLoading(true);
    try {
      await api.delete<OkResponse>(`/personas/${record.id}`);
      message.success('已删除');
      fetchItems();
    } catch (e) {
      message.error(getApiErrorDetail(e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card
      title="人设管理"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => openModal()}>
          新增人设
        </Button>
      }
    >
      <Table<Persona>
        rowKey="id"
        loading={loading}
        dataSource={items}
        pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
        scroll={{ x: 900 }}
        columns={[
          { title: 'ID', dataIndex: 'id', width: 60 },
          { title: '名称', dataIndex: 'name', width: 160 },
          {
            title: '配置（config JSON）',
            dataIndex: 'config',
            render: (config: unknown) => {
              const text = stringifyConfig(config);
              return (
                <Tooltip
                  title={<pre style={{ margin: 0, maxWidth: 480, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{text}</pre>}
                >
                  <Typography.Text code ellipsis style={{ maxWidth: 380 }}>
                    {text || '—'}
                  </Typography.Text>
                </Tooltip>
              );
            },
          },
          {
            title: '默认人设',
            dataIndex: 'is_default',
            width: 100,
            render: (v: boolean) => (v ? <Tag color="gold">默认</Tag> : <Tag>否</Tag>),
          },
          {
            title: '全局人设',
            dataIndex: 'is_global',
            width: 100,
            render: (v: boolean) => (v ? <Tag color="blue">全局</Tag> : <Tag>否</Tag>),
          },
          {
            title: '操作',
            key: 'actions',
            width: 140,
            fixed: 'right',
            render: (_, record) => (
              <Space>
                <Button size="small" icon={<EditOutlined />} onClick={() => openModal(record)}>
                  编辑
                </Button>
                <Popconfirm
                  title="删除人设"
                  description="确定删除该人设吗？"
                  okText="删除"
                  cancelText="取消"
                  okButtonProps={{ danger: true }}
                  onConfirm={() => handleDelete(record)}
                >
                  <Button size="small" danger icon={<DeleteOutlined />}>
                    删除
                  </Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title={editing ? `编辑人设：${editing.name}` : '新增人设'}
        open={open}
        onOk={handleSubmit}
        onCancel={closeModal}
        okText={editing ? '保存' : '创建'}
        cancelText="取消"
        confirmLoading={saving}
        width={640}
        destroyOnHidden
      >
        <Form<PersonaFormValues> form={form} layout="vertical" style={{ marginTop: 8 }}>
          <Form.Item name="name" label="人设名称" rules={[{ required: true, message: '请输入人设名称' }]}>
            <Input placeholder="例：暖男学长 / 严谨同事" />
          </Form.Item>
          <Form.Item
            name="config"
            label="配置（config，JSON 格式）"
            rules={[{ required: true, message: '请输入 JSON 配置' }]}
            extra="示例：{&quot;system_prompt&quot;: &quot;你是一个暖男学长...&quot;, &quot;temperature&quot;: 0.7}"
          >
            <TextArea rows={8} style={{ fontFamily: 'monospace' }} placeholder='{"system_prompt": "...", "temperature": 0.7}' />
          </Form.Item>
          <Space size={32}>
            <Form.Item name="is_default" label="设为默认人设" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="is_global" label="设为全局人设" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Card>
  );
}
