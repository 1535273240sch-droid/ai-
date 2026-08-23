/**
 * 接口类型定义 —— 严格对齐 docs/API_CONTRACT.md 字段
 */

// ---------- 认证 auth ----------
export interface User {
  id: number;
  username: string;
  role: string;
}

export interface LoginResponse {
  token: string;
  user: User;
}

// ---------- 卡密 license ----------
export type LicenseStatus = 'unused' | 'active' | 'revoked' | 'expired' | 'inactive';

export interface LicenseItem {
  code: string;
  status: LicenseStatus;
  activated_by?: string | null;
  device_fingerprint?: string | null;
  expires_at: string;
  features?: Record<string, unknown> | null;
  created_at: string;
}

export interface LicenseGenRequest {
  count: number;
  days: number;
  features?: Record<string, unknown> | null;
}

export interface LicenseGenResponse {
  codes: string[];
}

export interface LicenseListResponse {
  items: LicenseItem[];
}

export interface OkResponse {
  ok: true;
}

// ---------- 联系人 contacts ----------
export interface ContactProfile {
  relationship?: string;
  interaction_style?: string;
  reply_frequency?: string;
  sentence_style?: string;
  taboos?: string[];
}

export interface Contact {
  id: number;
  platform: string;
  platform_contact_id: string;
  nickname: string;
  profile: ContactProfile | null;
  created_at: string;
}

export interface ContactListResponse {
  items: Contact[];
}

// ---------- 人设 personas ----------
export interface Persona {
  id: number;
  name: string;
  // config 为任意 JSON
  config: unknown;
  is_default: boolean;
  is_global: boolean;
}

export interface PersonaListResponse {
  items: Persona[];
}

// ---------- 审计 audit ----------
export interface AuditLog {
  id: number;
  event: string;
  payload: unknown;
  created_at: string;
}

export interface AuditLogsResponse {
  items: AuditLog[];
  total: number;
}

// ---------- 看板 dashboard ----------
export interface DashboardStats {
  message_count: number;
  reply_count: number;
  active_contacts: number;
  persona_count: number;
  license_count: number;
  active_license_count: number;
  recent_logs: AuditLog[];
}
