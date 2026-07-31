import {beforeEach, describe, expect, it, vi} from 'vitest';
import {
  clearAccessToken,
  setAccessToken,
} from './accessTokenStore';
import {
  chargeWallet,
  fetchWalletBalance,
  refundWallet,
} from './walletApi';

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

  it('멱등키와 금액으로 Wallet을 충전한다', async () => {
    const transaction = {
      transactionId: 7,
      transactionType: 'CHARGE',
      amount: 50_000,
      balance: 150_000,
    };
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify(transaction), {
        status: 200,
        headers: {'Content-Type': 'application/json'},
      }));
    setAccessToken('wallet-access-token');

    await expect(chargeWallet({
      amount: 50_000,
      idempotencyKey: 'charge-attempt-id',
    })).resolves.toEqual(transaction);

    const [, options] = fetchMock.mock.calls[0] ?? [];
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/wallet/charges',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({amount: 50_000}),
      }),
    );
    expect(new Headers(options?.headers).get('Authorization'))
      .toBe('Bearer wallet-access-token');
    expect(new Headers(options?.headers).get('Idempotency-Key'))
      .toBe('charge-attempt-id');
  });

  it('멱등키와 금액으로 Wallet을 환불한다', async () => {
    const transaction = {
      transactionId: 8,
      transactionType: 'REFUND',
      amount: -10_000,
      balance: 90_000,
    };
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify(transaction), {
        status: 200,
        headers: {'Content-Type': 'application/json'},
      }));
    setAccessToken('wallet-access-token');

    await expect(refundWallet({
      amount: 10_000,
      idempotencyKey: 'refund-attempt-id',
    })).resolves.toEqual(transaction);

    const [, options] = fetchMock.mock.calls[0] ?? [];
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/wallet/refunds',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({amount: 10_000}),
      }),
    );
    expect(new Headers(options?.headers).get('Authorization'))
      .toBe('Bearer wallet-access-token');
    expect(new Headers(options?.headers).get('Idempotency-Key'))
      .toBe('refund-attempt-id');
  });

  it('안전한 정수가 아닌 Wallet 거래 응답을 거부한다', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({
        transactionId: 9,
        transactionType: 'CHARGE',
        amount: 1_000,
        balance: Number.MAX_SAFE_INTEGER + 1,
      }), {
        status: 200,
        headers: {'Content-Type': 'application/json'},
      }));
    setAccessToken('wallet-access-token');

    await expect(chargeWallet({
      amount: 1_000,
      idempotencyKey: 'unsafe-response',
    })).rejects.toThrow('Wallet 거래 응답이 올바르지 않습니다.');
  });
});
