import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {authenticatedRequest, optionallyAuthenticatedRequest} from './authenticatedRequest';
import {clearCsrfToken, setCsrfToken} from '../auth/session/csrfTokenStore';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {'Content-Type': 'application/json'},
  });
}

describe('authenticatedRequest', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    clearCsrfToken();
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('인증 요청은 세션 cookie를 포함하고 Bearer 토큰을 보내지 않는다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({id: 7}));

    await authenticatedRequest('/api/account');

    const [, options] = fetchMock.mock.calls[0];
    const headers = new Headers(options?.headers);
    expect(options?.credentials).toBe('include');
    expect(headers.has('Authorization')).toBe(false);
  });

  it('상태 변경 인증 요청은 현재 CSRF token을 함께 보낸다', async () => {
    setCsrfToken('csrf-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({}));

    await authenticatedRequest('/api/wallet/charges', {method: 'POST'});

    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('X-CSRF-Token'))
      .toBe('csrf-token');
  });

  it('401 응답은 refresh 재시도 없이 호출자에게 전달한다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({}, 401));

    await expect(authenticatedRequest('/api/wallet')).rejects.toMatchObject({status: 401});

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/wallet');
  });

  it('선택 인증 요청도 세션 cookie를 포함한다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({content: []}));

    await expect(optionallyAuthenticatedRequest('/api/auctions')).resolves.toEqual({content: []});

    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({credentials: 'include'});
  });
});
