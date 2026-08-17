import {authenticatedRequest} from './authenticatedRequest';

export type AdminAccountStatus = 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN';

export type AdminAccountDto = {
  id: number;
  email: string;
  nickname: string;
  role: 'USER' | 'ADMIN';
  status: AdminAccountStatus;
  created_at: string;
  active_warning_count: number;
  latest_active_warning_expires_at: string | null;
};

export type AdminAccountPageDto = {
  content: AdminAccountDto[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
  suspension_threshold: number;
};

export type AdminAccountWarningDto = {
  id: number;
  order_id: number | null;
  reason: string;
  issued_at: string;
  expires_at: string;
};

export function fetchAdminAccounts({page = 0, size = 20, keyword, status, onlyWarned}: {page?: number; size?: number; keyword?: string; status?: AdminAccountStatus; onlyWarned?: boolean} = {}) {
  const params = new URLSearchParams({page: String(page), size: String(size)});
  if (keyword?.trim()) params.set('keyword', keyword.trim());
  if (status) params.set('status', status);
  if (onlyWarned) params.set('only_warned', 'true');
  return authenticatedRequest<AdminAccountPageDto>(`/api/admin/users?${params}`);
}

export function fetchAdminAccountWarnings(userId: number) {
  return authenticatedRequest<AdminAccountWarningDto[]>(`/api/admin/users/${userId}/warnings`);
}

export function suspendAdminAccount(userId: number) {
  return authenticatedRequest<void>(`/api/admin/users/${userId}/suspend`, {method: 'POST'});
}

export function activateAdminAccount(userId: number) {
  return authenticatedRequest<void>(`/api/admin/users/${userId}/activate`, {method: 'POST'});
}

export function warnAdminAccount(userId: number) {
  return authenticatedRequest<void>(`/api/admin/users/${userId}/warn`, {method: 'POST'});
}
