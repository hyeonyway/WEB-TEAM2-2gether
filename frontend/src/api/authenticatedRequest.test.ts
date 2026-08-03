import {beforeEach, describe, expect, it, vi} from 'vitest';
import {clearAccessToken, getAccessToken, setAccessToken} from './accessTokenStore';
import {authenticatedRequest,optionallyAuthenticatedRequest} from './authenticatedRequest';
import {clearDebugUserId, setDebugUserId} from './debugAuthStorage';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {'Content-Type': 'application/json'},
  });
}

describe('authenticatedRequest', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    clearAccessToken();
    clearDebugUserId();
  });

  it('Bearer가 있으면 debug header를 함께 보내지 않는다', async () => {
    setAccessToken('access-token');
    setDebugUserId(7);
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({id: 7}));

    await authenticatedRequest('/api/account');

    const [, options] = fetchMock.mock.calls[0];
    const headers = new Headers(options?.headers);
    expect(headers.get('Authorization')).toBe('Bearer access-token');
    expect(headers.has('X-Debug-User-Id')).toBe(false);
  });

  it('동시 401은 Refresh 한 건을 공유하고 새 토큰으로 각 요청을 한 번 재시도한다', async () => {
    setAccessToken('expired-access-token');
    let resolveRefresh!: (response: Response) => void;
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockImplementation((input, options) => {
        const path = String(input);
        if (path === '/api/auth/refresh') {
          return new Promise(resolve => {
            resolveRefresh = resolve;
          });
        }
        const authorization = new Headers(options?.headers).get('Authorization');
        if (authorization === 'Bearer expired-access-token') {
          return Promise.resolve(jsonResponse({}, 401));
        }
        return Promise.resolve(jsonResponse({path, authorization}));
      });

    const accountRequest = authenticatedRequest<{path: string; authorization: string}>(
      '/api/account',
    );
    const walletRequest = authenticatedRequest<{path: string; authorization: string}>(
      '/api/wallet',
    );
    await vi.waitFor(() => {
      expect(fetchMock.mock.calls.filter(([path]) => path === '/api/auth/refresh'))
        .toHaveLength(1);
    });

    resolveRefresh(jsonResponse({accessToken: 'refreshed-access-token'}));

    await expect(Promise.all([accountRequest, walletRequest])).resolves.toEqual([
      {
        path: '/api/account',
        authorization: 'Bearer refreshed-access-token',
      },
      {
        path: '/api/wallet',
        authorization: 'Bearer refreshed-access-token',
      },
    ]);
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/account')).toHaveLength(2);
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/wallet')).toHaveLength(2);
    expect(getAccessToken()).toBe('refreshed-access-token');
  });

  it('Refresh 실패 시 토큰을 제거하고 원 요청을 재시도하지 않는다', async () => {
    setAccessToken('expired-access-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({}, 401))
      .mockResolvedValueOnce(jsonResponse({}, 401));

    await expect(authenticatedRequest('/api/account'))
      .rejects.toMatchObject({status: 401});

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1][0]).toBe('/api/auth/refresh');
    expect(getAccessToken()).toBeNull();
  });

  it('Refresh 성공 뒤 재시도도 401이면 토큰을 제거하고 더 반복하지 않는다', async () => {
    setAccessToken('expired-access-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({}, 401))
      .mockResolvedValueOnce(jsonResponse({accessToken: 'new-access-token'}))
      .mockResolvedValueOnce(jsonResponse({}, 401));

    await expect(authenticatedRequest('/api/account'))
      .rejects.toMatchObject({status: 401});

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(getAccessToken()).toBeNull();
  });

  it('선택적 인증 요청은 Refresh 실패 후 익명으로 재시도한다',async()=>{
    setAccessToken('expired-access-token');
    const fetchMock=vi.spyOn(globalThis,'fetch')
      .mockResolvedValueOnce(jsonResponse({},401))
      .mockResolvedValueOnce(jsonResponse({},401))
      .mockResolvedValueOnce(jsonResponse({content:[]}));

    await expect(optionallyAuthenticatedRequest('/api/auctions'))
      .resolves.toEqual({content:[]});

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(new Headers(fetchMock.mock.calls[2]?.[1]?.headers).get('Authorization')).toBeNull();
    expect(getAccessToken()).toBeNull();
  });
});
