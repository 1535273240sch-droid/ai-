/**
 * 统一 API 客户端：baseURL = /api/v1（由 Vite 代理到 http://localhost:8000）
 * - 自动注入 Authorization: Bearer <JWT>
 * - 401 自动清除 token 并跳转 /login
 * - 统一错误格式 { detail: string }
 */

export const API_BASE = '/api/v1';
export const TOKEN_KEY = 'auth_token';
export const USER_KEY = 'auth_user';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function getUser(): { id: number; username: string; role: string } | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as { id: number; username: string; role: string };
  } catch {
    return null;
  }
}

export function setUser(user: { id: number; username: string; role: string }): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export class ApiError extends Error {
  status: number;
  detail: string;

  constructor(status: number, detail: string) {
    super(detail);
    this.name = 'ApiError';
    this.status = status;
    this.detail = detail;
  }
}

interface RequestOptions extends RequestInit {
  skipAuthRedirect?: boolean;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { skipAuthRedirect = false, ...init } = options;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init.headers as Record<string, string> | undefined),
  };
  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let res: Response;
  try {
    res = await fetch(`${API_BASE}${path}`, { ...init, headers });
  } catch (e) {
    throw new ApiError(0, '网络请求失败，请确认后端服务已启动（http://localhost:8000）');
  }

  if (res.status === 401 && !skipAuthRedirect) {
    clearAuth();
    if (window.location.pathname !== '/login') {
      window.location.href = '/login';
    }
    throw new ApiError(401, '未登录或登录已过期');
  }

  const text = await res.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }

  if (!res.ok) {
    let detail = `请求失败（HTTP ${res.status}）`;
    if (data && typeof data === 'object' && 'detail' in data) {
      const raw = (data as { detail: unknown }).detail;
      if (Array.isArray(raw)) {
        // pydantic 422 的 detail 是数组：{detail:[{loc,msg}]}，拼接 msg
        detail = raw
          .map((it: { msg?: string }) => it?.msg ?? String(it))
          .filter(Boolean)
          .join('；');
      } else if (typeof raw === 'string') {
        detail = raw;
      } else {
        detail = JSON.stringify(raw);
      }
    } else if (typeof data === 'string') {
      detail = data;
    }
    throw new ApiError(res.status, detail);
  }

  return data as T;
}

export const api = {
  get: <T>(path: string): Promise<T> => request<T>(path),
  post: <T>(path: string, body?: unknown): Promise<T> =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(path: string, body?: unknown): Promise<T> =>
    request<T>(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) }),
  delete: <T>(path: string): Promise<T> => request<T>(path, { method: 'DELETE' }),
};

export function getApiErrorDetail(e: unknown): string {
  if (e instanceof ApiError) return e.detail;
  if (e instanceof Error) return e.message;
  return '未知错误';
}
