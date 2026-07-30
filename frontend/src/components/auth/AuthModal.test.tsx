import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {HttpError} from '../../api/httpClient';
import Header from '../Header';

const {loginMock, signupMock} = vi.hoisted(() => ({
  loginMock: vi.fn(),
  signupMock: vi.fn(),
}));

vi.mock('../../api/authApi', async importOriginal => {
  const actual = await importOriginal<typeof import('../../api/authApi')>();
  return {
    ...actual,
    login: loginMock,
    signup: signupMock,
  };
});

function renderHeader() {
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: {retry: false},
      queries: {retry: false},
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <Header/>
    </QueryClientProvider>,
  );
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

  it('유효하지 않은 입력과 비밀번호 불일치를 서버에 보내지 않는다', async () => {
    const user = await openSignupForm();

    await user.type(screen.getByLabelText('이메일'), 'invalid-email');
    await user.type(screen.getByLabelText('비밀번호'), 'short');
    await user.type(screen.getByLabelText('비밀번호 확인'), 'different');
    await user.type(screen.getByLabelText('닉네임'), '가');
    await user.click(screen.getByRole('button', {name: '회원가입'}));

    expect(await screen.findByText('올바른 이메일 주소를 입력해 주세요.')).toBeInTheDocument();
    expect(screen.getByText('비밀번호는 8자 이상 128자 이하로 입력해 주세요.')).toBeInTheDocument();
    expect(screen.getByText('비밀번호가 일치하지 않습니다.')).toBeInTheDocument();
    expect(screen.getByText('닉네임은 2자 이상 30자 이하로 입력해 주세요.')).toBeInTheDocument();
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
    await user.type(within(dialog).getByLabelText('이메일'), 'invalid-email');

    await user.click(within(dialog).getByRole('button', {name: '로그인'}));

    expect(await within(dialog).findByText('올바른 이메일 주소를 입력해 주세요.'))
      .toBeInTheDocument();
    expect(within(dialog).getByText('비밀번호를 입력해 주세요.')).toBeInTheDocument();
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
