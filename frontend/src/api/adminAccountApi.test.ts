import {beforeEach, describe, expect, it, vi} from 'vitest';
import {clearCsrfToken, setCsrfToken} from '../auth/session/csrfTokenStore';
import {activateAdminAccount, fetchAdminAccountWarnings, fetchAdminAccounts, suspendAdminAccount} from './adminAccountApi';

describe('adminAccountApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    clearCsrfToken();
  });

  it('검색어와 페이지로 관리자 회원 목록을 조회한다', async () => {
    const response = {content: [], page: 1, size: 20, total_elements: 0, total_pages: 0, suspension_threshold: 3};
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(response), {
      status: 200, headers: {'Content-Type': 'application/json'},
    }));

    await expect(fetchAdminAccounts({page: 1, keyword: '피카츄'})).resolves.toEqual(response);
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/users?page=1&size=20&keyword=%ED%94%BC%EC%B9%B4%EC%B8%84', expect.any(Object));
  });

  it('회원 경고 이력을 조회한다', async () => {
    const response = [{id: 1, order_id: 8, reason: 'ORDER_CANCELLATION', issued_at: '2026-08-15T00:00:00Z', expires_at: '2026-09-15T00:00:00Z'}];
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(response), {
      status: 200, headers: {'Content-Type': 'application/json'},
    }));

    await expect(fetchAdminAccountWarnings(7)).resolves.toEqual(response);
  });

  it.each([
    ['suspend', suspendAdminAccount],
    ['activate', activateAdminAccount],
  ] as const)('%s 상태 전환 요청에 CSRF 토큰을 포함한다', async (action, request) => {
    setCsrfToken('admin-csrf-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, {status: 204}));

    await expect(request(7)).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith(`/api/admin/users/7/${action}`, expect.objectContaining({method: 'POST'}));
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('X-CSRF-Token')).toBe('admin-csrf-token');
  });
});
