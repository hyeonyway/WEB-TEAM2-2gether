import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen, waitFor} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {clearAccessToken} from '../api/accessTokenStore';
import {AuthProvider} from '../auth/AuthProvider';
import {useAuth} from '../auth/useAuth';
import {useWalletBalance} from './walletQueries';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {'Content-Type': 'application/json'},
  });
}

function WalletProbe() {
  const {status} = useAuth();
  const walletQuery = useWalletBalance();
  return (
    <>
      <output data-testid="auth-status">{status}</output>
      <output data-testid="wallet-status">{walletQuery.status}</output>
      {walletQuery.data && (
        <output data-testid="wallet-balance">
          {walletQuery.data.totalBalance}/
          {walletQuery.data.frozenBalance}/
          {walletQuery.data.availableBalance}
        </output>
      )}
    </>
  );
}

function renderWalletProbe() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {retry: false},
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AuthProvider>
          <WalletProbe/>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('useWalletBalance', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    clearAccessToken();
  });

  it('anonymous 상태에서는 Wallet API를 호출하지 않는다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({code: 'REFRESH_TOKEN_MISSING'}, 401));

    renderWalletProbe();

    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('anonymous');
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/refresh',
      expect.objectContaining({method: 'POST'}),
    );
    expect(screen.queryByTestId('wallet-balance')).not.toBeInTheDocument();
  });

  it('authenticated 상태에서 같은 Query key로 Wallet 잔액을 조회한다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockImplementation(async input => {
        if (input === '/api/auth/refresh') {
          return jsonResponse({accessToken: 'restored-access-token'});
        }
        if (input === '/api/wallet') {
          return jsonResponse({
            totalBalance: 850_000,
            frozenBalance: 120_000,
            availableBalance: 730_000,
          });
        }
        throw new Error(`unexpected request: ${String(input)}`);
      });

    renderWalletProbe();

    expect(await screen.findByTestId('wallet-balance'))
      .toHaveTextContent('850000/120000/730000');
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/wallet'))
      .toHaveLength(1);
  });
});
