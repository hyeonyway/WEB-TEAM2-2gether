import {beforeEach, describe, expect, it, vi} from 'vitest';
import {clearAccessToken, getAccessToken, setAccessToken} from './accessTokenStore';
import {login, logout, refreshAccessToken, signup} from './authApi';

const signupResponse = {
  id: 1,
  email: 'collector@example.com',
  nickname: '포켓컬렉터',
  role: 'USER',
  status: 'ACTIVE',
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {'Content-Type': 'application/json'},
  });
}

describe('authApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    clearAccessToken();
  });

  it('회원가입 요청을 Refresh 쿠키를 포함할 수 있는 옵션으로 전송한다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse(signupResponse, 201));
    const request = {
      email: 'collector@example.com',
      password: 'Password123!',
      nickname: '포켓컬렉터',
    };

    await expect(signup(request)).resolves.toEqual(signupResponse);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/signup',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        body: JSON.stringify(request),
      }),
    );
  });

  it('로그인 성공 시 응답의 Access Token을 메모리에 보관한다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({accessToken: 'issued-access-token'}));
    const request = {
      email: 'collector@example.com',
      password: 'Password123!',
    };

    await login(request);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/login',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        body: JSON.stringify(request),
      }),
    );
    expect(getAccessToken()).toBe('issued-access-token');
  });

  it('Refresh 쿠키로 새 Access Token을 발급받아 메모리에 보관한다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({accessToken: 'refreshed-access-token'}));

    await expect(refreshAccessToken()).resolves.toEqual({
      accessToken: 'refreshed-access-token',
    });

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/refresh',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
      }),
    );
    expect(getAccessToken()).toBe('refreshed-access-token');
  });

  it('Refresh 실패 시 기존 Access Token을 제거한다', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({code: 'INVALID_REFRESH_TOKEN'}, 401));
    setAccessToken('expired-access-token');

    await expect(refreshAccessToken()).rejects.toMatchObject({status: 401});

    expect(getAccessToken()).toBeNull();
  });

  it('로그아웃 요청이 실패해도 메모리의 Access Token을 제거한다', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('network error'));
    setAccessToken('access-token');

    await expect(logout()).rejects.toThrow('network error');

    expect(getAccessToken()).toBeNull();
  });

  it('로그아웃을 Refresh 쿠키와 함께 요청하고 Access Token을 제거한다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(null, {status: 204}));
    setAccessToken('access-token');

    await logout();

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/logout',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
      }),
    );
    expect(getAccessToken()).toBeNull();
  });
});
