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

  it('안전한 정수가 아닌 Wallet 금액 응답을 거부한다', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({
        totalBalance: Number.MAX_SAFE_INTEGER + 1,
        frozenBalance: 0,
        availableBalance: Number.MAX_SAFE_INTEGER + 1,
      }), {
        status: 200,
        headers: {'Content-Type': 'application/json'},
      }));
    setAccessToken('wallet-access-token');

    await expect(fetchWalletBalance())
      .rejects.toThrow('Wallet 잔액 응답이 안전한 정수가 아닙니다.');
  });
});
