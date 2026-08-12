import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {clearAccessToken, getAccessToken} from '../api/accessTokenStore';
import {AuthProvider} from './AuthProvider';
import {useAuth} from './useAuth';
import {clearCsrfToken, getCsrfToken, setCsrfToken} from './session/csrfTokenStore';
import {getSessionUserId, setSessionUserId} from './session/sessionAuthStore';

vi.mock('../hooks/useWalletStream', () => ({useWalletStream: vi.fn()}));

class BroadcastChannelMock extends EventTarget {
  static instances: BroadcastChannelMock[] = [];
  postMessage = vi.fn();
  close = vi.fn();

  constructor(public name: string) {
    super();
    BroadcastChannelMock.instances.push(this);
  }
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {'Content-Type': 'application/json'},
  });
}

function AuthStatusProbe() {
  const {status} = useAuth();
  return <output data-testid="auth-status">{status}</output>;
}

function RetryInitializationButton() {
  const {retryInitialization} = useAuth();
  return <button type="button" onClick={retryInitialization}>인증 복구 요청</button>;
}

function renderAuthProvider() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {retry: false},
    },
  });

  return {
    queryClient,
    ...render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AuthProvider>
          <span>공개 화면</span>
          <AuthStatusProbe/>
          <RetryInitializationButton/>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
    ),
  };
}

describe('AuthProvider 앱 시작 인증 복구', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    BroadcastChannelMock.instances = [];
    vi.stubGlobal('BroadcastChannel', BroadcastChannelMock);
    clearAccessToken();
    clearCsrfToken();
    setSessionUserId(null);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('Refresh 성공 전에는 initializing이고 성공하면 authenticated가 된다', async () => {
    let resolveRefresh!: (response: Response) => void;
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockReturnValue(new Promise(resolve => {
        resolveRefresh = resolve;
      }));

    renderAuthProvider();

    expect(screen.getByTestId('auth-status')).toHaveTextContent('initializing');
    expect(screen.getByText('공개 화면')).toBeInTheDocument();

    resolveRefresh(jsonResponse({accessToken: 'restored-access-token'}));

    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('authenticated');
    });
    expect(getAccessToken()).toBe('restored-access-token');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('Refresh 401이면 공개 화면을 유지하며 anonymous가 된다', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({code: 'REFRESH_TOKEN_MISSING'}, 401));

    renderAuthProvider();

    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('anonymous');
    });
    expect(screen.getByText('공개 화면')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('세션 모드에서는 현재 사용자와 CSRF token으로 인증을 복구한다', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'session');
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({userId: 37}))
      .mockResolvedValueOnce(jsonResponse({csrfToken: 'session-csrf-token'}));

    renderAuthProvider();

    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('authenticated');
    });
    expect(getCsrfToken()).toBe('session-csrf-token');
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual([
      '/api/auth/me',
      '/api/auth/csrf',
    ]);
    expect(fetchMock.mock.calls).not.toContainEqual(
      expect.arrayContaining(['/api/auth/refresh']),
    );
  });

  it('세션 인증 복구가 실패하면 이전 사용자와 CSRF token을 제거한다', async () => {
    vi.stubEnv('VITE_AUTH_MODE', 'session');
    setSessionUserId(37);
    setCsrfToken('stale-csrf-token');
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({code: 'SESSION_EXPIRED'}, 401));

    renderAuthProvider();

    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('anonymous');
    });
    expect(getSessionUserId()).toBeNull();
    expect(getCsrfToken()).toBeNull();
  });

  it('네트워크 실패 시 전역 오류를 노출하지 않고 수동 복구할 수 있다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockRejectedValueOnce(new TypeError('network error'))
      .mockResolvedValueOnce(jsonResponse({accessToken: 'retried-access-token'}));
    const user = userEvent.setup();

    renderAuthProvider();

    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('anonymous');
    });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: '인증 복구 요청'}));

    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('authenticated');
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(getAccessToken()).toBe('retried-access-token');
  });

  it('인증 복구 중 재시도 요청이 겹쳐도 Refresh를 한 번만 호출한다', async () => {
    let resolveRefresh!: (response: Response) => void;
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockReturnValue(new Promise(resolve => {
        resolveRefresh = resolve;
      }));
    const user = userEvent.setup();

    renderAuthProvider();
    await user.click(screen.getByRole('button', {name: '인증 복구 요청'}));

    expect(fetchMock).toHaveBeenCalledTimes(1);

    resolveRefresh(jsonResponse({accessToken: 'restored-access-token'}));
    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('authenticated');
    });
  });

  it('anonymous 전환 시 개인 Query cache만 제거한다', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({code: 'REFRESH_TOKEN_MISSING'}, 401));
    const {queryClient} = renderAuthProvider();
    queryClient.setQueryData(['auth', 'me'], {id: 1});
    queryClient.setQueryData(['account', 'profile'], {nickname: '포켓컬렉터'});
    queryClient.setQueryData(['wallet', 'balance'], {totalBalance: 10_000});
    queryClient.setQueryData(['auction', 'catalog'], [{id: 1}]);

    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('anonymous');
    });

    expect(queryClient.getQueryData(['auth', 'me'])).toBeUndefined();
    expect(queryClient.getQueryData(['account', 'profile'])).toBeUndefined();
    expect(queryClient.getQueryData(['wallet', 'balance'])).toBeUndefined();
    expect(queryClient.getQueryData(['auction', 'catalog'])).toEqual([{id: 1}]);
  });

  it('다른 탭의 Wallet 변경 신호를 받으면 Wallet 잔액을 다시 조회한다', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({accessToken: 'restored-access-token'}));
    const {queryClient} = renderAuthProvider();
    queryClient.setQueryData(['wallet', 'balance'], {
      totalBalance: 100_000,
      frozenBalance: 20_000,
      availableBalance: 80_000,
    });

    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('authenticated');
    });
    expect(BroadcastChannelMock.instances).toHaveLength(1);

    BroadcastChannelMock.instances[0]?.dispatchEvent(new MessageEvent('message', {
      data: {type: 'WALLET_CHANGED'},
    }));

    await waitFor(() => {
      expect(queryClient.getQueryState(['wallet', 'balance'])?.isInvalidated).toBe(true);
    });
  });
});
