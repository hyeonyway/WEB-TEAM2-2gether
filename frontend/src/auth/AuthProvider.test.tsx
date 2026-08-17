import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen, waitFor} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {AuthProvider} from './AuthProvider';
import {useAuth} from './useAuth';
import {clearCsrfToken, getCsrfToken, setCsrfToken} from './session/csrfTokenStore';
import {getSessionUserId, setSession} from './session/sessionAuthStore';

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
  return new Response(JSON.stringify(body), {status, headers: {'Content-Type': 'application/json'}});
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
  const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
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

describe('AuthProvider 앱 시작 세션 인증 복구', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    BroadcastChannelMock.instances = [];
    vi.stubGlobal('BroadcastChannel', BroadcastChannelMock);
    clearCsrfToken();
    setSession(null);
  });

  it('현재 사용자와 CSRF token을 모두 조회한 뒤 authenticated가 된다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({userId: 37}))
      .mockResolvedValueOnce(jsonResponse({csrfToken: 'session-csrf-token'}));

    renderAuthProvider();

    await waitFor(() => expect(screen.getByTestId('auth-status')).toHaveTextContent('authenticated'));
    expect(getSessionUserId()).toBe(37);
    expect(getCsrfToken()).toBe('session-csrf-token');
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/api/auth/me', '/api/auth/csrf']);
    expect(fetchMock.mock.calls.every(([, options]) => options?.credentials === 'include')).toBe(true);
  });

  it('세션 복구가 401이면 공개 화면을 유지하고 세션 상태를 비운다', async () => {
    setSession(37);
    setCsrfToken('stale-csrf-token');
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({code: 'SESSION_EXPIRED'}, 401));

    renderAuthProvider();

    await waitFor(() => expect(screen.getByTestId('auth-status')).toHaveTextContent('anonymous'));
    expect(getSessionUserId()).toBeNull();
    expect(getCsrfToken()).toBeNull();
    expect(screen.getByText('공개 화면')).toBeInTheDocument();
  });

  it('네트워크 실패 뒤 수동 복구하면 세션 인증을 다시 조회한다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockRejectedValueOnce(new TypeError('network error'))
      .mockResolvedValueOnce(jsonResponse({userId: 7}))
      .mockResolvedValueOnce(jsonResponse({csrfToken: 'retried-csrf-token'}));
    const user = userEvent.setup();

    renderAuthProvider();
    await waitFor(() => expect(screen.getByTestId('auth-status')).toHaveTextContent('anonymous'));

    await user.click(screen.getByRole('button', {name: '인증 복구 요청'}));

    await waitFor(() => expect(screen.getByTestId('auth-status')).toHaveTextContent('authenticated'));
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(getSessionUserId()).toBe(7);
  });

  it('동시에 재시도해도 현재 사용자 조회는 한 번만 실행한다', async () => {
    let resolveCurrent!: (response: Response) => void;
    let calls = 0;
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(() => {
      calls += 1;
      if (calls === 1) return new Promise(resolve => {
        resolveCurrent = resolve;
      });
      return Promise.resolve(jsonResponse({csrfToken: 'csrf-token'}));
    });
    const user = userEvent.setup();

    renderAuthProvider();
    await user.click(screen.getByRole('button', {name: '인증 복구 요청'}));
    expect(fetchMock).toHaveBeenCalledTimes(1);

    resolveCurrent(jsonResponse({userId: 7}));
    await waitFor(() => expect(screen.getByTestId('auth-status')).toHaveTextContent('authenticated'));
  });

  it('anonymous 전환 시 개인 Query cache만 제거한다', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({}, 401));
    const {queryClient} = renderAuthProvider();
    queryClient.setQueryData(['auth', 'me'], {id: 1});
    queryClient.setQueryData(['account', 'profile'], {nickname: '포켓컬렉터'});
    queryClient.setQueryData(['wallet', 'balance'], {totalBalance: 10_000});
    queryClient.setQueryData(['auction', 'catalog'], [{id: 1}]);

    await waitFor(() => expect(screen.getByTestId('auth-status')).toHaveTextContent('anonymous'));

    expect(queryClient.getQueryData(['auth', 'me'])).toBeUndefined();
    expect(queryClient.getQueryData(['account', 'profile'])).toBeUndefined();
    expect(queryClient.getQueryData(['wallet', 'balance'])).toBeUndefined();
    expect(queryClient.getQueryData(['auction', 'catalog'])).toEqual([{id: 1}]);
  });
});
