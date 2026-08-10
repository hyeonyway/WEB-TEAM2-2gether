import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, useLocation} from 'react-router-dom';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import type {NotificationDto} from '../dto/notificationDto';
import NotificationBell from './NotificationBell';

const {useNotificationsMock} = vi.hoisted(() => ({
  useNotificationsMock: vi.fn(),
}));

vi.mock('../hooks/useNotifications', () => ({
  useNotifications: useNotificationsMock,
}));

function LocationProbe(){
  const{pathname,search}=useLocation();
  return <output data-testid="path">{pathname}{search}</output>;
}

function mockNotifications(notifications:NotificationDto[]){
  return {
    isLoggedIn: true,
    notifications,
    unreadCount: 0,
    isLoading: false,
    isError: false,
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
    refetchAll: vi.fn(),
    markAsRead: vi.fn(),
    markAllAsRead: vi.fn(),
  };
}

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

class NoopIntersectionObserver{
  observe=vi.fn();
  disconnect=vi.fn();
  unobserve=vi.fn();
  takeRecords=()=>[];
  readonly root=null;
  readonly rootMargin='0px';
  readonly thresholds=[0];
}

describe('NotificationBell 이동 버튼',()=>{
  beforeEach(()=>{
    vi.stubGlobal('IntersectionObserver',NoopIntersectionObserver);
  });

  it('경매 관련 알림은 이동 버튼 클릭 시 경매 상세로 이동한다',async()=>{
    const notification:NotificationDto={id:1,auctionId:7,type:'OUTBID',message:'상회 입찰이 발생했습니다.',isRead:false,createdAt:'2026-08-03T12:00:00'};
    useNotificationsMock.mockReturnValue(mockNotifications([notification]));
    const user=userEvent.setup();
    render(
      <MemoryRouter>
        <NotificationBell/>
        <LocationProbe/>
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('button',{name:'알림'}));
    await user.click(screen.getByRole('button',{name:'이동'}));

    expect(screen.getByTestId('path')).toHaveTextContent(`/auction/${notification.auctionId}`);
  });

  it('주문 관련 알림(ORDER_COMPLETED)은 이동 버튼 클릭 시 대시보드 주문 탭으로 이동한다',async()=>{
    const notification:NotificationDto={id:2,auctionId:7,type:'ORDER_COMPLETED',message:'주문이 완료되었습니다.',isRead:false,createdAt:'2026-08-03T12:00:00'};
    useNotificationsMock.mockReturnValue(mockNotifications([notification]));
    const user=userEvent.setup();
    render(
      <MemoryRouter>
        <NotificationBell/>
        <LocationProbe/>
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('button',{name:'알림'}));
    await user.click(screen.getByRole('button',{name:'이동'}));

    expect(screen.getByTestId('path')).toHaveTextContent('/dashboard?tab=orders');
  });
});
