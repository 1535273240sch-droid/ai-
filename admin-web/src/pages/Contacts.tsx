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
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { api, getApiErrorDetail } from '../api/client';
import type { Contact, ContactProfile, OkResponse } from '../api/types';

const { TextArea } = Input;

interface ContactFormValues {
  platform: string;
  platform_contact_id: string;
  nickname: string;
  relationship?: string;
  interaction_style?: string;
  reply_frequency?: string;
  sentence_style?: string;
  taboos?: string;
}

/** 逗号 / 中文逗号 / 顿号 / 换行分隔的列表解析 */
function parseList(raw?: string): string[] {
  if (!raw || !raw.trim()) return [];
  return raw
    .split(/[,\n，、]/)
    .map((s) => s.trim())
    .filter(Boolean);
}

function profileSummary(p: ContactProfile | null | undefined): string {
  if (!p) return '';
  const parts: string[] = [];
  if (p.relationship) parts.push(`关系：${p.relationship}`);
  if (p.interaction_style) parts.push(`互动风格：${p.interaction_style}`);
  if (p.reply_frequency) parts.push(`回复频率：${p.reply_frequency}`);
  if (p.sentence_style) parts.push(`句式风格：${p.sentence_style}`);
  if (p.taboos && p.taboos.length > 0) parts.push(`禁忌：${p.taboos.join('、')}`);
  return parts.join('\n');
}

export default function Contacts() {
  const { message } = App.useApp();
  const [form] = Form.useForm<ContactFormValues>();
  const [items, setItems] = useState<Contact[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Contact | null>(null);
  const [saving, setSaving] = useState(false);

  const fetchItems = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<{ items: Contact[] }>('/contacts');
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

  const openModal = (record?: Contact) => {
    setEditing(record ?? null);
    setOpen(true);
    if (record) {
      form.setFieldsValue({
        platform: record.platform,
        platform_contact_id: record.platform_contact_id,
        nickname: record.nickname,
        relationship: record.profile?.relationship ?? '',
        interaction_style: record.profile?.interaction_style ?? '',
        reply_frequency: record.profile?.reply_frequency ?? '',
        sentence_style: record.profile?.sentence_style ?? '',
        taboos: (record.profile?.taboos ?? []).join('，'),
      });
    } else {
      form.resetFields();
    }
  };

  const closeModal = () => {
    setOpen(false);
    setEditing(null);
    form.resetFields();
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const profile: ContactProfile = {
        relationship: values.relationship?.trim() || undefined,
        interaction_style: values.interaction_style?.trim() || undefined,
        reply_frequency: values.reply_frequency?.trim() || undefined,
        sentence_style: values.sentence_style?.trim() || undefined,
        taboos: parseList(values.taboos),
      };
      if (editing) {
        await api.put<{ contact: Contact }>(`/contacts/${editing.id}`, {
          platform: values.platform,
          platform_contact_id: values.platform_contact_id,
          nickname: values.nickname,
          profile,
        });
        message.success('联系人已更新');
      } else {
        await api.post<{ contact: Contact }>('/contacts', {
          platform: values.platform,
          platform_contact_id: values.platform_contact_id,
          nickname: values.nickname,
          profile,
        });
        message.success('联系人已创建');
      }
      closeModal();
      fetchItems();
    } catch (e) {
      message.error(getApiErrorDetail(e));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (record: Contact) => {
    setLoading(true);
    try {
      await api.delete<OkResponse>(`/contacts/${record.id}`);
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
      title="联系人管理"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => openModal()}>
          新增联系人
        </Button>
      }
    >
      <Table<Contact>
        rowKey="id"
        loading={loading}
        dataSource={items}
        pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
        scroll={{ x: 1000 }}
        columns={[
          { title: 'ID', dataIndex: 'id', width: 60 },
          { title: '平台', dataIndex: 'platform', width: 120, render: (v: string) => <Tag>{v}</Tag> },
          { title: '平台联系人ID', dataIndex: 'platform_contact_id', width: 180 },
          { title: '昵称', dataIndex: 'nickname', width: 140 },
          {
            title: '画像（profile）',
            key: 'profile',
            width: 320,
            render: (_, record) => {
              const summary = profileSummary(record.profile);
              return summary ? (
                <Tooltip title={<pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{summary}</pre>}>
                  <Typography.Text ellipsis style={{ maxWidth: 300 }}>
                    {summary.split('\n')[0]}
                  </Typography.Text>
                </Tooltip>
              ) : (
                <Typography.Text type="secondary">—</Typography.Text>
              );
            },
          },
          { title: '创建时间', dataIndex: 'created_at', width: 180 },
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
                  title="删除联系人"
                  description="确定删除该联系人及其画像吗？"
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
        title={editing ? `编辑联系人：${editing.nickname}` : '新增联系人'}
        open={open}
        onOk={handleSubmit}
        onCancel={closeModal}
        okText={editing ? '保存' : '创建'}
        cancelText="取消"
        confirmLoading={saving}
        width={640}
        destroyOnHidden
      >
        <Form<ContactFormValues> form={form} layout="vertical" style={{ marginTop: 8 }}>
          <Space.Compact style={{ display: 'flex', width: '100%' }} block>
            <Form.Item
              name="platform"
              label="平台"
              rules={[{ required: true, message: '请输入平台' }]}
              style={{ width: '40%', marginRight: 8 }}
            >
              <Input placeholder="如 wechat / qq / telegram" />
            </Form.Item>
            <Form.Item
              name="platform_contact_id"
              label="平台联系人ID"
              rules={[{ required: true, message: '请输入平台联系人ID' }]}
              style={{ width: '60%' }}
            >
              <Input placeholder="对方在平台上的唯一ID" />
            </Form.Item>
          </Space.Compact>
          <Form.Item name="nickname" label="昵称" rules={[{ required: true, message: '请输入昵称' }]}>
            <Input placeholder="联系人昵称" />
          </Form.Item>

          <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
            联系人画像（可留空，多行文本）
          </Typography.Text>
          <Form.Item name="relationship" label="relationship 关系描述">
            <TextArea rows={2} placeholder="例：这是我大学同学，关系比较熟" />
          </Form.Item>
          <Form.Item name="interaction_style" label="interaction_style 互动风格">
            <TextArea rows={2} placeholder="例：热情友好，语气轻松，偶尔用网络流行语" />
          </Form.Item>
          <Form.Item name="reply_frequency" label="reply_frequency 回复频率">
            <TextArea rows={2} placeholder="例：消息发出后 5 分钟内回复，深夜不打扰" />
          </Form.Item>
          <Form.Item name="sentence_style" label="sentence_style 句式风格">
            <TextArea rows={2} placeholder="例：简短口语化，多用语气词，不用书面语" />
          </Form.Item>
          <Form.Item name="taboos" label="taboos 禁忌话题（逗号或换行分隔）">
            <TextArea rows={3} placeholder="例：不聊政治，不聊前任，不谈收入" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
