import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, useLocation} from 'react-router-dom';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
} from '../../api/accessTokenStore';
import {HttpError} from '../../api/httpClient';
import {AuthProvider} from '../../auth/AuthProvider';
import {useAuth} from '../../auth/useAuth';
import '../../tailwind.css';
import Header from '../Header';

const {loginMock, refreshMock, signupMock} = vi.hoisted(() => ({
  loginMock: vi.fn(),
  refreshMock: vi.fn(),
  signupMock: vi.fn(),
}));

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {'Content-Type': 'application/json'},
  });
}

vi.mock('../../api/authApi', async importOriginal => {
  const actual = await importOriginal<typeof import('../../api/authApi')>();
  return {
    ...actual,
    login: loginMock,
    refreshAccessToken: refreshMock,
    signup: signupMock,
  };
});

function LocationProbe() {
  const {pathname} = useLocation();
  return <output data-testid="router-path">{pathname}</output>;
}

function AuthStatusProbe() {
  const {status} = useAuth();
  return <output data-testid="auth-status">{status}</output>;
}

function renderHeader(path = window.location.pathname) {
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: {retry: false},
      queries: {retry: false},
    },
  });

  return {
    queryClient,
    ...render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[path]}>
          <AuthProvider>
            <Header/>
            <LocationProbe/>
            <AuthStatusProbe/>
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    ),
  };
}

async function openSignupForm() {
  renderHeader();
  const user = userEvent.setup();
  await user.click(screen.getByRole('button', {name: '로그인'}));
  await user.click(screen.getByRole('button', {name: '회원가입하기'}));
  return user;
}

async function fillValidSignup(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('이메일'), 'collector@example.com');
  await user.type(screen.getByLabelText('비밀번호'), 'Password123!');
  await user.type(screen.getByLabelText('비밀번호 확인'), 'Password123!');
  await user.type(screen.getByLabelText('닉네임'), '포켓컬렉터');
}

beforeEach(() => {
  refreshMock.mockReset();
  refreshMock.mockImplementation(async () => {
    const accessToken = getAccessToken();
    if (accessToken) return {accessToken};
    throw new HttpError(401, 'unauthorized');
  });
});

afterEach(() => {
  clearAccessToken();
  vi.restoreAllMocks();
});

describe('Header 마이페이지 인증 gate', () => {
  beforeEach(() => {
    loginMock.mockReset();
    window.history.replaceState({}, '', '/auction');
  });

  it('anonymous 사용자는 이동하지 않고 로그인 모달을 연다', async () => {
    const user = userEvent.setup();
    renderHeader('/auction');
    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('anonymous');
    });

    await user.click(screen.getByRole('link', {name: '마이페이지'}));

    expect(screen.getByTestId('router-path')).toHaveTextContent('/auction');
    expect(screen.getByRole('dialog', {name: '계정 로그인'})).toBeInTheDocument();
  });

  it('보호 진입 로그인 성공 뒤 마이페이지로 이동한다', async () => {
    loginMock.mockImplementation(async () => {
      setAccessToken('issued-access-token');
      return {accessToken: 'issued-access-token'};
    });
    const user = userEvent.setup();
    renderHeader('/auction');
    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('anonymous');
    });
    await user.click(screen.getByRole('link', {name: '마이페이지'}));
    const dialog = screen.getByRole('dialog', {name: '계정 로그인'});
    await user.type(within(dialog).getByLabelText('이메일'), 'collector@example.com');
    await user.type(within(dialog).getByLabelText('비밀번호'), 'Password123!');

    await user.click(within(dialog).getByRole('button', {name: '로그인'}));

    await waitFor(() => {
      expect(screen.getByTestId('router-path')).toHaveTextContent('/mypage');
    });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('보호 진입 로그인 모달을 닫으면 홈으로 이동한다', async () => {
    const user = userEvent.setup();
    renderHeader('/auction');
    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('anonymous');
    });
    await user.click(screen.getByRole('link', {name: '마이페이지'}));

    await user.click(screen.getByRole('button', {name: '인증 모달 닫기'}));

    expect(screen.getByTestId('router-path')).toHaveTextContent('/');
  });

  it('일반 로그인과 보호 진입이 겹쳐도 닫기 시 모달 상태를 모두 초기화한다', async () => {
    const user = userEvent.setup();
    renderHeader('/auction');
    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('anonymous');
    });
    await user.click(screen.getByRole('button', {name: '로그인'}));
    await user.click(screen.getByRole('link', {name: '마이페이지'}));

    await user.click(screen.getByRole('button', {name: '인증 모달 닫기'}));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByTestId('router-path')).toHaveTextContent('/');
  });

  it('initializing 중 요청한 마이페이지 이동을 인증 복구 뒤 이어간다', async () => {
    let resolveRefresh!: () => void;
    refreshMock.mockImplementation(() => new Promise(resolve => {
      resolveRefresh = () => {
        setAccessToken('restored-access-token');
        resolve({accessToken: 'restored-access-token'});
      };
    }));
    const user = userEvent.setup();
    renderHeader('/auction');

    await user.click(screen.getByRole('link', {name: '마이페이지'}));

    expect(screen.getByTestId('router-path')).toHaveTextContent('/auction');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    resolveRefresh();

    await waitFor(() => {
      expect(screen.getByTestId('router-path')).toHaveTextContent('/mypage');
    });
  });
});

describe('Header 계정 메뉴', () => {
  it('로그인과 마이페이지에 같은 글자 크기를 적용한다', () => {
    window.history.replaceState({}, '', '/');
    renderHeader();

    const myPageLink = screen.getByRole('link', {name: '마이페이지'});
    const loginButton = screen.getByRole('button', {name: '로그인'});

    expect(getComputedStyle(loginButton).fontSize)
      .toBe(getComputedStyle(myPageLink).fontSize);
    expect(getComputedStyle(loginButton).fontSize).toBe('11px');
  });

  it('React Router의 현재 경로를 기준으로 활성 메뉴를 표시한다', () => {
    window.history.replaceState({}, '', '/');

    renderHeader('/auction');

    expect(screen.getByRole('link', {name: '카드 경매'}))
      .toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', {name: '홈'}))
      .not.toHaveAttribute('aria-current');
  });
});

describe('Header Wallet 잔액', () => {
  it('anonymous 상태에서는 전자지갑과 충전 진입점을 숨긴다', async () => {
    renderHeader('/');

    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('anonymous');
    });
    expect(screen.queryByRole('button', {name: /전자지갑/}))
      .not.toBeInTheDocument();
    expect(screen.queryByText('충전하기')).not.toBeInTheDocument();
  });

  it('인증 복구 중에는 전자지갑과 Wallet skeleton을 렌더링하지 않는다', () => {
    refreshMock.mockReturnValue(new Promise(() => {}));

    renderHeader('/');

    expect(screen.getByTestId('auth-status')).toHaveTextContent('initializing');
    expect(screen.queryByRole('button', {name: /전자지갑/}))
      .not.toBeInTheDocument();
    expect(screen.queryByRole('status', {name: '전자지갑 잔액 불러오는 중'}))
      .not.toBeInTheDocument();
  });

  it('authenticated 상태의 Wallet 조회 중에 skeleton을 표시한다', async () => {
    setAccessToken('access-token');
    vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise(() => {}));

    renderHeader('/');

    expect(await screen.findByRole('status', {name: '전자지갑 잔액 불러오는 중'}))
      .toBeInTheDocument();
    expect(screen.queryByText('850,000P')).not.toBeInTheDocument();
  });

  it('Wallet 조회 성공 시 서버 totalBalance와 충전 진입점을 표시한다', async () => {
    setAccessToken('access-token');
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({
      totalBalance: 987_654,
      frozenBalance: 120_000,
      availableBalance: 867_654,
    }));

    renderHeader('/');

    expect(await screen.findByText('987,654P')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /전자지갑.*987,654P.*충전하기/}))
      .toBeInTheDocument();
  });
});

describe('AuthModal 회원가입', () => {
  beforeEach(() => {
    signupMock.mockReset();
    window.history.replaceState({}, '', '/auction');
  });

  it('현재 경로를 유지하며 Header에서 열고 바깥 영역과 Escape로 닫는다', async () => {
    const user = userEvent.setup();
    renderHeader();

    await user.click(screen.getByRole('button', {name: '로그인'}));

    expect(screen.getByRole('dialog', {name: '계정 로그인'})).toBeInTheDocument();
    expect(window.location.pathname).toBe('/auction');

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: '로그인'}));
    await user.click(screen.getByTestId('auth-modal-backdrop'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('닫기 버튼으로 모달을 닫는다', async () => {
    const user = userEvent.setup();
    renderHeader();

    await user.click(screen.getByRole('button', {name: '로그인'}));
    await user.click(screen.getByRole('button', {name: '인증 모달 닫기'}));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('열릴 때 첫 입력으로 이동하고 닫히면 실행 버튼으로 포커스를 돌려준다', async () => {
    const user = userEvent.setup();
    renderHeader();
    const openButton = screen.getByRole('button', {name: '로그인'});

    await user.click(openButton);

    expect(screen.getByLabelText('이메일')).toHaveFocus();

    await user.keyboard('{Escape}');

    expect(openButton).toHaveFocus();
  });

  it('회원가입 모드에서 닫았다가 다시 열어도 로그인 입력에 포커스를 둔다', async () => {
    const user = userEvent.setup();
    renderHeader();
    const openButton = screen.getByRole('button', {name: '로그인'});

    await user.click(openButton);
    await user.click(screen.getByRole('button', {name: '회원가입하기'}));
    await user.keyboard('{Escape}');
    await user.click(openButton);

    expect(screen.getByRole('dialog', {name: '계정 로그인'})).toBeInTheDocument();
    expect(screen.getByLabelText('이메일')).toHaveFocus();
  });

  it('Tab 포커스를 모달 내부에서 순환시킨다', async () => {
    const user = userEvent.setup();
    renderHeader();

    await user.click(screen.getByRole('button', {name: '로그인'}));

    const closeButton = screen.getByRole('button', {name: '인증 모달 닫기'});
    const switchButton = screen.getByRole('button', {name: '회원가입하기'});
    closeButton.focus();

    await user.tab({shift: true});
    expect(switchButton).toHaveFocus();

    await user.tab();
    expect(closeButton).toHaveFocus();
  });

  it('유효하지 않은 입력과 비밀번호 불일치를 서버에 보내지 않는다', async () => {
    const user = await openSignupForm();
    const emailInput = screen.getByLabelText('이메일');
    const passwordInput = screen.getByLabelText('비밀번호');
    const passwordConfirmationInput = screen.getByLabelText('비밀번호 확인');
    const nicknameInput = screen.getByLabelText('닉네임');

    expect(emailInput).toHaveAttribute('type', 'email');
    await user.type(emailInput, 'invalid-email');
    await user.type(passwordInput, 'short');
    await user.type(passwordConfirmationInput, 'different');
    await user.type(nicknameInput, '가');
    await user.click(screen.getByRole('button', {name: '회원가입'}));

    const emailError = await screen.findByText('올바른 이메일 주소를 입력해 주세요.');
    const passwordError = screen.getByText('비밀번호는 8자 이상 128자 이하로 입력해 주세요.');
    const passwordConfirmationError = screen.getByText('비밀번호가 일치하지 않습니다.');
    const nicknameError = screen.getByText('닉네임은 2자 이상 30자 이하로 입력해 주세요.');
    expect(emailInput).toHaveAttribute('aria-describedby', 'signup-email-error');
    expect(passwordInput).toHaveAttribute('aria-describedby', 'signup-password-error');
    expect(passwordConfirmationInput)
      .toHaveAttribute('aria-describedby', 'signup-password-confirmation-error');
    expect(nicknameInput).toHaveAttribute('aria-describedby', 'signup-nickname-error');
    expect(emailError).toHaveAttribute('id', 'signup-email-error');
    expect(passwordError).toHaveAttribute('id', 'signup-password-error');
    expect(passwordConfirmationError)
      .toHaveAttribute('id', 'signup-password-confirmation-error');
    expect(nicknameError).toHaveAttribute('id', 'signup-nickname-error');
    for (const error of [emailError, passwordError, passwordConfirmationError, nicknameError]) {
      expect(error).toHaveAttribute('role', 'alert');
    }
    expect(signupMock).not.toHaveBeenCalled();
  });

  it('중복 응답은 이메일과 닉네임을 구분하지 않는 공통 메시지로 표시한다', async () => {
    signupMock.mockRejectedValue(new HttpError(409, 'conflict'));
    const user = await openSignupForm();
    await fillValidSignup(user);

    await user.click(screen.getByRole('button', {name: '회원가입'}));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '이미 사용 중인 이메일 또는 닉네임입니다.',
    );
  });

  it('가입 성공 후 이메일과 완료 안내를 유지한 채 로그인 모드로 전환한다', async () => {
    signupMock.mockResolvedValue({
      id: 1,
      email: 'collector@example.com',
      nickname: '포켓컬렉터',
      role: 'USER',
      status: 'ACTIVE',
    });
    const user = await openSignupForm();
    await fillValidSignup(user);

    await user.click(screen.getByRole('button', {name: '회원가입'}));

    await waitFor(() => expect(signupMock).toHaveBeenCalledTimes(1));
    expect(await screen.findByText('가입이 완료되었습니다. 로그인해 주세요.')).toBeInTheDocument();
    expect(screen.getByLabelText('이메일')).toHaveValue('collector@example.com');
    expect(screen.getByRole('dialog', {name: '계정 로그인'})).toBeInTheDocument();
  });

  it('요청 중에는 제출 버튼을 비활성화해 중복 요청을 막는다', async () => {
    signupMock.mockReturnValue(new Promise(() => {}));
    const user = await openSignupForm();
    await fillValidSignup(user);

    const submit = screen.getByRole('button', {name: '회원가입'});
    await user.click(submit);
    await user.click(submit);

    expect(submit).toBeDisabled();
    expect(signupMock).toHaveBeenCalledTimes(1);
  });
});

describe('AuthModal 로그인', () => {
  beforeEach(() => {
    loginMock.mockReset();
    window.history.replaceState({}, '', '/auction/7');
  });

  async function openLoginForm() {
    const user = userEvent.setup();
    renderHeader();
    await user.click(screen.getByRole('button', {name: '로그인'}));
    return {
      dialog: screen.getByRole('dialog', {name: '계정 로그인'}),
      user,
    };
  }

  async function fillValidLogin(
    user: ReturnType<typeof userEvent.setup>,
    dialog: HTMLElement,
  ) {
    await user.type(within(dialog).getByLabelText('이메일'), 'collector@example.com');
    await user.type(within(dialog).getByLabelText('비밀번호'), 'Password123!');
  }

  it('유효하지 않은 이메일과 빈 비밀번호를 서버에 보내지 않는다', async () => {
    const {dialog, user} = await openLoginForm();
    const emailInput = within(dialog).getByLabelText('이메일');
    const passwordInput = within(dialog).getByLabelText('비밀번호');

    expect(emailInput).toHaveAttribute('type', 'email');
    await user.type(emailInput, 'invalid-email');

    await user.click(within(dialog).getByRole('button', {name: '로그인'}));

    const emailError = await within(dialog).findByText('올바른 이메일 주소를 입력해 주세요.');
    const passwordError = within(dialog).getByText('비밀번호를 입력해 주세요.');
    expect(emailInput).toHaveAttribute('aria-describedby', 'login-email-error');
    expect(passwordInput).toHaveAttribute('aria-describedby', 'login-password-error');
    expect(emailError).toHaveAttribute('id', 'login-email-error');
    expect(passwordError).toHaveAttribute('id', 'login-password-error');
    expect(emailError).toHaveAttribute('role', 'alert');
    expect(passwordError).toHaveAttribute('role', 'alert');
    expect(loginMock).not.toHaveBeenCalled();
  });

  it('128자를 초과한 비밀번호를 서버에 보내지 않는다', async () => {
    const {dialog, user} = await openLoginForm();
    await user.type(within(dialog).getByLabelText('이메일'), 'collector@example.com');
    await user.type(within(dialog).getByLabelText('비밀번호'), 'a'.repeat(129));

    await user.click(within(dialog).getByRole('button', {name: '로그인'}));

    expect(await within(dialog).findByText('비밀번호는 128자 이하로 입력해 주세요.'))
      .toBeInTheDocument();
    expect(loginMock).not.toHaveBeenCalled();
  });

  it('401 응답은 계정 존재 여부를 드러내지 않는 공통 메시지로 표시한다', async () => {
    loginMock.mockRejectedValue(new HttpError(401, 'unauthorized'));
    const {dialog, user} = await openLoginForm();
    await fillValidLogin(user, dialog);

    await user.click(within(dialog).getByRole('button', {name: '로그인'}));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      '이메일 또는 비밀번호가 일치하지 않습니다.',
    );
  });

  it('네트워크 오류는 재시도 가능한 공통 메시지로 표시한다', async () => {
    loginMock.mockRejectedValue(new TypeError('network error'));
    const {dialog, user} = await openLoginForm();
    await fillValidLogin(user, dialog);

    await user.click(within(dialog).getByRole('button', {name: '로그인'}));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      '로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.',
    );
  });

  it('로그인 성공 시 현재 경로를 유지하며 모달을 닫는다', async () => {
    loginMock.mockResolvedValue({accessToken: 'access-token'});
    const {dialog, user} = await openLoginForm();
    await fillValidLogin(user, dialog);

    await user.click(within(dialog).getByRole('button', {name: '로그인'}));

    await waitFor(() => expect(loginMock).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(window.location.pathname).toBe('/auction/7');
  });

  it('요청 중에는 제출 버튼을 비활성화해 중복 요청을 막는다', async () => {
    loginMock.mockReturnValue(new Promise(() => {}));
    const {dialog, user} = await openLoginForm();
    await fillValidLogin(user, dialog);

    const submit = within(dialog).getByRole('button', {name: '로그인'});
    await user.click(submit);
    await user.click(submit);

    expect(submit).toBeDisabled();
    expect(loginMock).toHaveBeenCalledTimes(1);
  });
});

describe('Header 로그아웃', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', '/auction');
  });

  it('서버에 한 번 요청하고 토큰과 인증 cache를 정리한 뒤 홈으로 이동한다', async () => {
    let resolveLogout!: (response: Response) => void;
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockImplementation(input => {
        if (input === '/api/wallet') {
          return Promise.resolve(jsonResponse({
            totalBalance: 10_000,
            frozenBalance: 0,
            availableBalance: 10_000,
          }));
        }
        return new Promise(resolve => {
          resolveLogout = resolve;
        });
      });
    setAccessToken('access-token');
    const {queryClient} = renderHeader();
    queryClient.setQueryData(['auth', 'me'], {id: 1});
    queryClient.setQueryData(['account', 'profile'], {id: 1});
    queryClient.setQueryData(['wallet', 'balance'], {totalBalance: 10_000});
    queryClient.setQueryData(['auction', 'catalog'], [{id: 1}]);
    const user = userEvent.setup();

    const logoutButton = screen.getByRole('button', {name: '로그아웃'});
    await user.click(logoutButton);
    await user.click(logoutButton);

    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/auth/logout'))
      .toHaveLength(1);
    resolveLogout(new Response(null, {status: 204}));
    await waitFor(() => expect(getAccessToken()).toBeNull());
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/logout',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
      }),
    );
    expect(getAccessToken()).toBeNull();
    expect(queryClient.getQueryData(['auth', 'me'])).toBeUndefined();
    expect(queryClient.getQueryData(['account', 'profile'])).toBeUndefined();
    expect(queryClient.getQueryData(['wallet', 'balance'])).toBeUndefined();
    expect(queryClient.getQueryData(['auction', 'catalog'])).toEqual([{id: 1}]);
    expect(screen.getByTestId('router-path')).toHaveTextContent('/');
    expect(screen.getByRole('button', {name: '로그인'})).toBeInTheDocument();
  });

  it('서버 요청이 실패해도 토큰과 인증 cache를 정리한다', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('network error'));
    setAccessToken('access-token');
    const {queryClient} = renderHeader();
    queryClient.setQueryData(['auth', 'me'], {id: 1});
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', {name: '로그아웃'}));

    await waitFor(() => expect(getAccessToken()).toBeNull());
    expect(queryClient.getQueryData(['auth', 'me'])).toBeUndefined();
  });
});
