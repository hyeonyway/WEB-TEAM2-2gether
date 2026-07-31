import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import {describe, expect, it, vi} from 'vitest';
import NotificationBell from './NotificationBell';

const {useNotificationsMock} = vi.hoisted(() => ({
  useNotificationsMock: vi.fn(),
}));

vi.mock('../hooks/useNotifications', () => ({
  useNotifications: useNotificationsMock,
}));

describe('NotificationBell 인증 안내', () => {
  it('비로그인 사용자는 drawer를 열지 않고 공통 토스트로 안내한다', async () => {
    useNotificationsMock.mockReturnValue({
      isLoggedIn: false,
      notifications: [],
      unreadCount: 0,
      isLoading: false,
      isError: false,
      hasNextPage: false,
      isFetchingNextPage: false,
      fetchNextPage: vi.fn(),
      refetchAll: vi.fn(),
      markAsRead: vi.fn(),
      markAllAsRead: vi.fn(),
    });
    const toastListener = vi.fn();
    window.addEventListener('app-toast', toastListener);
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <NotificationBell/>
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('button', {name: '알림'}));

    expect(screen.queryByRole('dialog', {name: '알림 목록'}))
      .not.toBeInTheDocument();
    expect(toastListener).toHaveBeenCalledOnce();
    expect((toastListener.mock.calls[0]?.[0] as CustomEvent).detail.message)
      .toBe('로그인이 필요합니다');
    window.removeEventListener('app-toast', toastListener);
  });
});
