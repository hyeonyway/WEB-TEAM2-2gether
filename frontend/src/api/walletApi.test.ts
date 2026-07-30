import {beforeEach, describe, expect, it, vi} from 'vitest';
import {
  clearAccessToken,
  setAccessToken,
} from './accessTokenStore';
import {fetchWalletBalance} from './walletApi';

describe('walletApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    clearAccessToken();
  });

  it('Access Token으로 Wallet 총액·동결액·가용액을 조회한다', async () => {
    const balance = {
      totalBalance: 850_000,
      frozenBalance: 120_000,
      availableBalance: 730_000,
    };
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify(balance), {
        status: 200,
        headers: {'Content-Type': 'application/json'},
      }));
    setAccessToken('wallet-access-token');

    await expect(fetchWalletBalance()).resolves.toEqual(balance);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/wallet',
      expect.objectContaining({headers: expect.any(Headers)}),
    );
    const requestOptions = fetchMock.mock.calls[0]?.[1];
    expect(new Headers(requestOptions?.headers).get('Authorization'))
      .toBe('Bearer wallet-access-token');
  });
});
