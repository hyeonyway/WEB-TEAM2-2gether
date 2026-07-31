import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {act, fireEvent, render, screen} from '@testing-library/react';
import {MemoryRouter, useLocation, useNavigate} from 'react-router-dom';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {AuthContext, type AuthStatus} from '../auth/AuthProvider';
import App from './App';

const protectedRoutes = [
  {path: '/sell', heading: '판매 등록', level: 1},
  {path: '/dashboard', heading: '경매 대시보드', level: 1},
  {path: '/mypage', heading: '계정 관리', level: 1},
];

function LocationProbe() {
  const {pathname} = useLocation();
  return <output data-testid="router-path">{pathname}</output>;
}

function BackButton() {
  const navigate = useNavigate();
  return <button type="button" onClick={() => navigate(-1)}>뒤로</button>;
}

function renderRoute(path: string, status: AuthStatus) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {retry: false},
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <AuthContext.Provider value={{status, retryInitialization: vi.fn()}}>
          <App/>
          <LocationProbe/>
          <BackButton/>
        </AuthContext.Provider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('AppRoutes', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise(() => {}));
  });

  afterEach(() => {
    act(() => {
      vi.runOnlyPendingTimers();
    });
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('공개 경로는 anonymous 상태에서도 표시한다', () => {
    renderRoute('/auction', 'anonymous');

    expect(screen.getByRole('heading', {name: '카드 경매'}))
      .toBeInTheDocument();
    expect(screen.queryByText('로그인이 필요합니다')).not.toBeInTheDocument();
  });

  it.each(protectedRoutes)(
    '$path는 initializing 중 보호 화면을 렌더링하지 않는다',
    ({path, heading, level}) => {
      renderRoute(path, 'initializing');

      expect(screen.getByText('인증 상태를 확인하고 있습니다.'))
        .toBeInTheDocument();
      expect(screen.queryByRole('heading', {name: heading, level}))
        .not.toBeInTheDocument();
      expect(screen.getByTestId('router-path')).toHaveTextContent(path);
    },
  );

  it.each(protectedRoutes)(
    '$path는 anonymous 상태에서 토스트를 표시하고 홈으로 대체 이동한다',
    ({path, heading, level}) => {
      const toastListener = vi.fn();
      window.addEventListener('app-toast', toastListener);

      renderRoute(path, 'anonymous');

      expect(screen.getByTestId('router-path')).toHaveTextContent('/');
      expect(screen.getByRole('heading', {name: '경매 인사이트'}))
        .toBeInTheDocument();
      expect(screen.queryByRole('heading', {name: heading, level}))
        .not.toBeInTheDocument();
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      expect(screen.getAllByText('로그인이 필요합니다')).toHaveLength(1);
      expect(toastListener).toHaveBeenCalledOnce();
      window.removeEventListener('app-toast', toastListener);
    },
  );

  it('anonymous 보호 경로 차단은 history를 replace한다', () => {
    renderRoute('/mypage', 'anonymous');
    expect(screen.getByTestId('router-path')).toHaveTextContent('/');

    fireEvent.click(screen.getByRole('button', {name: '뒤로'}));

    expect(screen.getByTestId('router-path')).toHaveTextContent('/');
  });

  it.each(protectedRoutes)(
    '$path는 authenticated 상태에서 보호 화면을 표시한다',
    ({path, heading, level}) => {
      renderRoute(path, 'authenticated');

      expect(screen.getByRole('heading', {name: heading, level}))
        .toBeInTheDocument();
      expect(screen.getByTestId('router-path')).toHaveTextContent(path);
      expect(screen.queryByText('로그인이 필요합니다')).not.toBeInTheDocument();
    },
  );
});
