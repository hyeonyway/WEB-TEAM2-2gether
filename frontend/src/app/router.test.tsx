import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen, waitFor, within} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {clearAccessToken} from '../api/accessTokenStore';
import {AuthProvider} from '../auth/AuthProvider';
import {AppRoutes} from './router';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {'Content-Type': 'application/json'},
  });
}

function renderRoute(path: string) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <AppRoutes/>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('AppRoutes', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    clearAccessToken();
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse({code: 'REFRESH_TOKEN_MISSING'}, 401));
  });

  it('기존 경매 목록 경로를 유지한다', () => {
    renderRoute('/auction');

    expect(screen.getByRole('heading', {name: '카드 경매'})).toBeInTheDocument();
  });

  it('인증 전용 경로를 만들지 않고 알 수 없는 경로는 홈으로 보낸다', () => {
    renderRoute('/login');

    expect(screen.getByRole('heading', {name: '경매 인사이트'})).toBeInTheDocument();
  });

  it('mypage 직접 접근은 인증 확인 중 내용을 숨기고 anonymous면 모달을 연다', async () => {
    renderRoute('/mypage');

    expect(screen.getByText('인증 상태를 확인하고 있습니다.')).toBeInTheDocument();
    expect(screen.queryByRole('heading', {name: '계정 관리', level: 1}))
      .not.toBeInTheDocument();

    expect(await screen.findByRole('dialog', {name: '계정 로그인'}))
      .toBeInTheDocument();
    expect(screen.queryByRole('heading', {name: '계정 관리', level: 1}))
      .not.toBeInTheDocument();
  });

  it('mypage 보호 모달을 닫으면 홈으로 이동한다', async () => {
    const user = userEvent.setup();
    renderRoute('/mypage');
    await screen.findByRole('dialog', {name: '계정 로그인'});

    await user.click(screen.getByRole('button', {name: '인증 모달 닫기'}));

    expect(await screen.findByRole('heading', {name: '경매 인사이트'}))
      .toBeInTheDocument();
  });

  it('mypage 보호 모달에서 로그인하면 페이지 내용을 표시한다', async () => {
    vi.mocked(globalThis.fetch).mockImplementation(async input => {
      if (input === '/api/auth/refresh') {
        return jsonResponse({code: 'REFRESH_TOKEN_MISSING'}, 401);
      }
      if (input === '/api/auth/login') {
        return jsonResponse({accessToken: 'issued-access-token'});
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
    const user = userEvent.setup();
    renderRoute('/mypage');
    await screen.findByRole('dialog', {name: '계정 로그인'});
    await user.type(screen.getByLabelText('이메일'), 'collector@example.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Password123!');

    await user.click(screen.getByRole('button', {name: '로그인'}));

    await waitFor(() => {
      expect(screen.getByRole('heading', {name: '계정 관리', level: 1}))
        .toBeInTheDocument();
    });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    const walletRegion = await screen.findByRole('region', {
      name: '전자지갑 잔액',
    });
    expect(within(walletRegion).getByText('850,000P')).toBeInTheDocument();
    expect(within(walletRegion).getByText('120,000P')).toBeInTheDocument();
    expect(within(walletRegion).getByText('730,000P')).toBeInTheDocument();
  });
});
