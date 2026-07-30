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

  it.each([
    ['/login', '로그인'],
    ['/signup', '회원가입'],
  ])('%s 인증 경로를 제공한다', (path, heading) => {
    renderRoute(path);

    expect(screen.getByRole('heading', {name: heading})).toBeInTheDocument();
  });
});
