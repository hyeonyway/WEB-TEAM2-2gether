import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {render,screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter,useLocation} from 'react-router-dom';
import {beforeEach,describe,expect,it,vi} from 'vitest';
import type {NotificationDto} from '../dto/notificationDto';
import * as notificationApi from '../api/notificationApi';
import NotificationToastStack from './NotificationToastStack';

const notification:NotificationDto={
  id:9,auctionId:5,message:'상회 입찰이 발생했습니다.',isRead:false,createdAt:'2026-08-03T12:00:00',
};

function LocationProbe(){
  const{pathname}=useLocation();
  return <output data-testid="path">{pathname}</output>;
}

function renderStack(toasts:NotificationDto[],onDismiss=vi.fn()){
  const queryClient=new QueryClient({defaultOptions:{mutations:{retry:false},queries:{retry:false}}});
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/']}>
        <NotificationToastStack toasts={toasts} onDismiss={onDismiss}/>
        <LocationProbe/>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return {onDismiss};
}

describe('NotificationToastStack',()=>{
  beforeEach(()=>{
    vi.restoreAllMocks();
    vi.spyOn(notificationApi,'markNotificationAsRead').mockResolvedValue(undefined);
  });

  it('토스트가 없으면 아무것도 렌더링하지 않는다',()=>{
    renderStack([]);

    expect(screen.queryByText(notification.message)).not.toBeInTheDocument();
  });

  it('본문을 클릭하면 읽음 처리하고 경매 상세로 이동한 뒤 팝업을 제거한다',async()=>{
    const user=userEvent.setup();
    const{onDismiss}=renderStack([notification]);

    await user.click(screen.getByText(notification.message));

    expect(notificationApi.markNotificationAsRead).toHaveBeenCalledWith(notification.id);
    expect(onDismiss).toHaveBeenCalledWith(notification.id);
    expect(screen.getByTestId('path')).toHaveTextContent(`/auction/${notification.auctionId}`);
  });

  it('X 버튼을 클릭하면 읽음 처리 없이 팝업만 제거한다',async()=>{
    const user=userEvent.setup();
    const{onDismiss}=renderStack([notification]);

    await user.click(screen.getByRole('button',{name:'닫기'}));

    expect(notificationApi.markNotificationAsRead).not.toHaveBeenCalled();
    expect(onDismiss).toHaveBeenCalledWith(notification.id);
    expect(screen.getByTestId('path')).toHaveTextContent('/');
  });

  it('여러 개를 순서대로 렌더링한다',()=>{
    const second={...notification,id:10,message:'낙찰되었습니다.'};
    renderStack([notification,second]);

    expect(screen.getByText(notification.message)).toBeInTheDocument();
    expect(screen.getByText(second.message)).toBeInTheDocument();
  });
});
