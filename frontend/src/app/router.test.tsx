import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {describe, expect, it} from 'vitest';
import {AppRoutes} from './router';

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
        <AppRoutes/>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('AppRoutes', () => {
  it('기존 경매 목록 경로를 유지한다', () => {
    renderRoute('/auction');

    expect(screen.getByRole('heading', {name: '카드 경매'})).toBeInTheDocument();
  });

  it('인증 전용 경로를 만들지 않고 알 수 없는 경로는 홈으로 보낸다', () => {
    renderRoute('/login');

    expect(screen.getByRole('heading', {name: '경매 인사이트'})).toBeInTheDocument();
  });
});
