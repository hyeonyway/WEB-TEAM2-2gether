import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, useLocation} from 'react-router-dom';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {HttpError} from '../../api/httpClient';
import {AuthContext} from '../../auth/AuthProvider';
import {clearCsrfToken, setCsrfToken} from '../../auth/session/csrfTokenStore';
import {setSessionUserId} from '../../auth/session/sessionAuthStore';
import {walletQueryKeys} from '../../queries/walletQueryKeys';
import Header from '../Header';

const {loginMock, logoutMock, signupMock} = vi.hoisted(() => ({
  loginMock: vi.fn(),
  logoutMock: vi.fn(),
  signupMock: vi.fn(),
}));

vi.mock('../../api/authApi', () => ({
  login: loginMock,
  logout: logoutMock,
  signup: signupMock,
}));

function LocationProbe() {
  return <output data-testid="router-path">{useLocation().pathname}</output>;
}

function renderHeader(status: 'authenticated' | 'anonymous' = 'anonymous', path = '/auction') {
  const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}, mutations: {retry: false}}});
  return {
    queryClient,
    ...render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[path]}>
          <AuthContext.Provider value={{status, retryInitialization: vi.fn()}}>
            <Header/>
            <LocationProbe/>
          </AuthContext.Provider>
        </MemoryRouter>
      </QueryClientProvider>,
    ),
  };
}

async function openLogin() {
  const user = userEvent.setup();
  await user.click(screen.getByRole('button', {name: '로그인'}));
  const dialog = screen.getByRole('dialog', {name: '계정 로그인'});
  return {user, dialog};
}

async function fillLogin(user: ReturnType<typeof userEvent.setup>, dialog: HTMLElement) {
  await user.type(within(dialog).getByLabelText('이메일'), 'collector@example.com');
  await user.type(within(dialog).getByLabelText('비밀번호'), 'Password123!');
}

describe('Header와 인증 모달의 세션 인증 UI', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    loginMock.mockReset();
    logoutMock.mockReset();
    signupMock.mockReset();
    clearCsrfToken();
    setSessionUserId(null);
  });

  it('anonymous 사용자는 보호 메뉴를 이동하지 않고 로그인 안내 토스트를 한 번 표시한다', async () => {
    const toastListener = vi.fn();
    window.addEventListener('app-toast', toastListener);
    const user = userEvent.setup();
    renderHeader();

    await user.click(screen.getByRole('link', {name: '판매 등록'}));

    expect(screen.getByTestId('router-path')).toHaveTextContent('/auction');
    expect(toastListener).toHaveBeenCalledOnce();
    expect((toastListener.mock.calls[0]?.[0] as CustomEvent).detail.message).toBe('로그인이 필요합니다');
    window.removeEventListener('app-toast', toastListener);
  });

  it('authenticated 상태에서는 가용 지갑 잔액과 충전 진입점을 표시한다', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async input => {
      if (String(input).includes('/api/wallet')) {
        return new Response(JSON.stringify({
          totalBalance: 100_000,
          frozenBalance: 30_000,
          availableBalance: 70_000,
        }), {headers: {'Content-Type': 'application/json'}});
      }
      return new Response(JSON.stringify({count: 0}), {headers: {'Content-Type': 'application/json'}});
    });
    renderHeader('authenticated');

    expect(await screen.findByRole('button', {name: /전자지갑.*70,000P.*충전하기/})).toBeInTheDocument();
  });

  it('로그인 성공 시 개인 query를 무효화하고 모달을 닫는다', async () => {
    loginMock.mockImplementation(async () => {
      setSessionUserId(7);
      setCsrfToken('csrf-token');
      return {csrfToken: 'csrf-token'};
    });
    const {queryClient} = renderHeader();
    const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries');
    const {user, dialog} = await openLogin();
    await fillLogin(user, dialog);

    await user.click(within(dialog).getByRole('button', {name: '로그인'}));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(loginMock).toHaveBeenCalledWith(
      {email: 'collector@example.com', password: 'Password123!'},
      expect.anything(),
    );
    expect(invalidateQueries).toHaveBeenCalledWith({queryKey: walletQueryKeys.all});
  });

  it('로그인 401은 계정 존재 여부를 드러내지 않는 공통 메시지로 표시한다', async () => {
    loginMock.mockRejectedValue(new HttpError(401, 'unauthorized'));
    renderHeader();
    const {user, dialog} = await openLogin();
    await fillLogin(user, dialog);

    await user.click(within(dialog).getByRole('button', {name: '로그인'}));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('이메일 또는 비밀번호가 일치하지 않습니다.');
  });

  it('회원가입 성공 후 이메일을 유지한 로그인 화면으로 돌아온다', async () => {
    signupMock.mockResolvedValue({id: 1, email: 'collector@example.com', nickname: '포켓컬렉터'});
    renderHeader();
    const {user} = await openLogin();
    await user.click(screen.getByRole('button', {name: '회원가입하기'}));
    await user.type(screen.getByLabelText('이메일'), 'collector@example.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Password123!');
    await user.type(screen.getByLabelText('비밀번호 확인'), 'Password123!');
    await user.type(screen.getByLabelText('닉네임'), '포켓컬렉터');

    await user.click(screen.getByRole('button', {name: '회원가입'}));

    expect(await screen.findByText('가입이 완료되었습니다. 로그인해 주세요.')).toBeInTheDocument();
    expect(screen.getByRole('dialog', {name: '계정 로그인'})).toBeInTheDocument();
    expect(screen.getByLabelText('이메일')).toHaveValue('collector@example.com');
  });

  it('로그아웃이 완료되면 개인 지갑 cache를 제거하고 홈으로 이동한다', async () => {
    logoutMock.mockImplementation(async () => {
      clearCsrfToken();
      setSessionUserId(null);
    });
    const {queryClient} = renderHeader('authenticated');
    queryClient.setQueryData(walletQueryKeys.balance(), {totalBalance: 10_000});
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', {name: '로그아웃'}));

    await waitFor(() => expect(logoutMock).toHaveBeenCalledOnce());
    expect(queryClient.getQueryData(walletQueryKeys.balance())).toBeUndefined();
    expect(screen.getByTestId('router-path')).toHaveTextContent('/');
  });
});
