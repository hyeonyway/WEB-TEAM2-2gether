import {beforeEach, describe, expect, it, vi} from 'vitest';
import {clearCsrfToken, setCsrfToken} from './csrfTokenStore';
import {sessionAuthenticatedRequest} from './sessionAuthenticatedRequest';

describe('sessionAuthenticatedRequest', () => {
  beforeEach(() => {
    clearCsrfToken();
    vi.restoreAllMocks();
  });

  it('상태 변경 요청에 cookie와 CSRF header를 함께 전송한다', async () => {
    setCsrfToken('csrf-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}'));

    await sessionAuthenticatedRequest('/api/wallet/charges', {method: 'POST'});

    const [, options] = fetchMock.mock.calls[0];
    expect(options?.credentials).toBe('include');
    expect(new Headers(options?.headers).get('X-CSRF-Token')).toBe('csrf-token');
  });

  it('GET 요청에는 CSRF header를 넣지 않는다', async () => {
    setCsrfToken('csrf-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}'));

    await sessionAuthenticatedRequest('/api/wallet');

    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).has('X-CSRF-Token')).toBe(false);
  });
});
