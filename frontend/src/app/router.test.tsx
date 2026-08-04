import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {act, fireEvent, render, screen} from '@testing-library/react';
import {MemoryRouter, useLocation, useNavigate} from 'react-router-dom';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {AuthContext, type AuthStatus} from '../auth/AuthProvider';
import {statisticQueryKeys} from '../queries/statisticQueries';
import App from './App';

const protectedRoutes = [
  {path: '/sell', heading: '판매 등록', level: 1},
  {path: '/dashboard', heading: '경매 대시보드', level: 1},
  {path: '/mypage', heading: '계정 관리', level: 1},
];

function LocationProbe() {
  const {pathname, search} = useLocation();
  return <output data-testid="router-path">{pathname}{search}</output>;
}

function BackButton() {
  const navigate = useNavigate();
  return <button type="button" onClick={() => navigate(-1)}>뒤로</button>;
}

function renderRoute(
  path: string,
  status: AuthStatus,
  prepareQueryClient?: (queryClient: QueryClient) => void,
) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {retry: false},
    },
  });
  prepareQueryClient?.(queryClient);

  const view = render(
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
  return {queryClient, ...view};
}

describe('AppRoutes', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', '/');
    vi.useFakeTimers();
    vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise(() => {}));
    vi.stubGlobal('IntersectionObserver', class {
      observe() {}
      disconnect() {}
    });
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }));
  });

  afterEach(() => {
    act(() => {
      vi.runOnlyPendingTimers();
    });
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('헤더의 카드 시세 링크는 SPA 내부에서 목록 경로로 이동한다', () => {
    renderRoute('/', 'anonymous');

    fireEvent.click(screen.getByRole('link', {name: '카드 시세'}));

    expect(screen.getByTestId('router-path')).toHaveTextContent('/cards');
    expect(screen.getByRole('heading', {name: '전체 카드 정보'}))
      .toBeInTheDocument();
  });

  it('Home 인사이트는 정렬 조건을 보존해 경매 목록으로 SPA 이동한다', () => {
    renderRoute('/', 'anonymous', queryClient => {
      queryClient.setQueryData(statisticQueryKeys.insights(), [{
        id: 'ACTIVE',
        title: '진행 경매',
        value: 12,
        changeRate: null,
        note: '현재 참여 가능한 경매',
        sort: 'PRICE_HIGH',
      }]);
    });

    fireEvent.click(screen.getByRole('link', {name: /진행 경매/}));

    expect(screen.getByTestId('router-path'))
      .toHaveTextContent('/auction?sort=PRICE_HIGH');
  });

  it('Home 가격 변동 카드는 Card 상세로 SPA 이동한다', () => {
    renderRoute('/', 'anonymous', queryClient => {
      queryClient.setQueryData(statisticQueryKeys.priceMovers(5), {
        periodDays: 30,
        gainers: [{
          cardId: 25,
          name: '리자몽',
          price: 300_000,
          changeRate: 8.5,
          theme: 'fire',
          bidCount: 7,
          imageUrl: null,
          currentDate: '2026-08-04',
          previousDate: '2026-08-03',
          priceHistory: [],
        }],
        losers: [],
      });
    });

    fireEvent.click(screen.getByRole('link', {name: /리자몽/}));

    expect(screen.getByTestId('router-path')).toHaveTextContent('/cards/25');
  });

  it.each([
    ['/dashboard', '전체 카드 경매'],
    ['/sell', '경매 목록'],
  ])('%s의 내부 링크는 경매 목록으로 SPA 이동한다', (path, linkName) => {
    renderRoute(path, 'authenticated');

    fireEvent.click(screen.getByRole('link', {name: linkName}));

    expect(screen.getByTestId('router-path')).toHaveTextContent('/auction');
  });

  it('Card 상세 직접 접근은 window pathname이 아니라 Router parameter를 사용한다', () => {
    window.history.replaceState({}, '', '/cards/999');

    renderRoute('/cards/42', 'anonymous');

    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/cards/42',
      expect.any(Object),
    );
  });

  it('경매 목록은 anonymous 상태에서도 표시한다', () => {
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

  it('경매 상세 경로는 anonymous 상태에서도 유지한다', () => {
    renderRoute('/auction/1', 'anonymous');

    expect(screen.getByTestId('router-path')).toHaveTextContent('/auction/1');
    expect(screen.queryByText('로그인이 필요합니다')).not.toBeInTheDocument();
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
