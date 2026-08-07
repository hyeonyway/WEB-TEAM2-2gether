import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {act,renderHook,waitFor} from '@testing-library/react';
import type {ReactNode} from 'react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {clearAccessToken,setAccessToken} from '../api/accessTokenStore';
import {setSessionUserId} from '../auth/session/sessionAuthStore';
import type {NotificationDto} from '../dto/notificationDto';
import {notificationQueryKeys} from '../queries/notificationQueries';
import {useNotificationStream} from './useNotificationStream';

const{issueSseTicketMock}=vi.hoisted(()=>({issueSseTicketMock:vi.fn()}));
vi.mock('../api/notificationTicketApi',()=>({issueSseTicket:issueSseTicketMock}));

class EventSourceMock extends EventTarget{
  static instances:EventSourceMock[]=[];
  readonly url:string;
	readonly options:EventSourceInit|undefined;
  close=vi.fn();
  onerror:(()=>void)|null=null;

  constructor(url:string|URL,options?:EventSourceInit){
    super();
    this.url=String(url);
		this.options=options;
    EventSourceMock.instances.push(this);
  }
}

function fakeAccessToken(userId:number):string{
  const payload=btoa(JSON.stringify({sub:String(userId)}));
  return `header.${payload}.signature`;
}

function publish(eventSource:EventSourceMock,data:object|string){
  eventSource.dispatchEvent(new MessageEvent('notification-created',{
    data:typeof data==='string'?data:JSON.stringify(data),
  }));
}

function createWrapper(){
  const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
  function Wrapper({children}:{children:ReactNode}){
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }
  return {queryClient,Wrapper};
}

const notification:NotificationDto={
  id:9,auctionId:5,message:'상회 입찰이 발생했습니다.',isRead:false,createdAt:'2026-08-03T12:00:00',
};

describe('useNotificationStream',()=>{
  beforeEach(()=>{
    EventSourceMock.instances=[];
    vi.stubGlobal('EventSource',EventSourceMock);
    issueSseTicketMock.mockReset();
    issueSseTicketMock.mockResolvedValue({ticket:'ticket-1',expiresInSeconds:30});
    setAccessToken(fakeAccessToken(42));
  });

  afterEach(()=>{
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
    clearAccessToken();
    setSessionUserId(null);
  });

  it('티켓을 발급받아 유저별 알림 스트림에 연결한다',async()=>{
    const{Wrapper}=createWrapper();
    const{unmount}=renderHook(()=>useNotificationStream(),{wrapper:Wrapper});

    await waitFor(()=>expect(EventSourceMock.instances).toHaveLength(1));
    expect(EventSourceMock.instances[0]?.url).toContain('/api/users/42/notifications/stream?ticket=ticket-1');
    unmount();
  });

  it('세션 모드에서는 ticket 없이 내 알림 스트림에 연결한다',async()=>{
    vi.stubEnv('VITE_AUTH_MODE','session');
    clearAccessToken();
    setSessionUserId(42);
    const{Wrapper}=createWrapper();
    const{unmount}=renderHook(()=>useNotificationStream(),{wrapper:Wrapper});

    await waitFor(()=>expect(EventSourceMock.instances).toHaveLength(1));
    expect(EventSourceMock.instances[0]?.url).toContain('/api/me/notifications/stream');
		expect(EventSourceMock.instances[0]?.options).toEqual({withCredentials:true});
    expect(issueSseTicketMock).not.toHaveBeenCalled();
    unmount();
  });

  it('알림 수신 시 목록·안읽음 캐시를 갱신하고 콜백을 호출한다',async()=>{
    const{queryClient,Wrapper}=createWrapper();
    queryClient.setQueryData(notificationQueryKeys.list(false),{pages:[{items:[],nextCursor:null,hasNext:false}],pageParams:[undefined]});
    queryClient.setQueryData(notificationQueryKeys.list(true),{pages:[{items:[],nextCursor:null,hasNext:false}],pageParams:[undefined]});
    queryClient.setQueryData(notificationQueryKeys.unreadCount,2);
    const onNotificationCreated=vi.fn();
    const{unmount}=renderHook(()=>useNotificationStream({onNotificationCreated}),{wrapper:Wrapper});
    await waitFor(()=>expect(EventSourceMock.instances).toHaveLength(1));

    act(()=>publish(EventSourceMock.instances[0]!,notification));

    expect(onNotificationCreated).toHaveBeenCalledWith(notification);
    expect(queryClient.getQueryData(notificationQueryKeys.list(false))).toEqual({
      pages:[{items:[notification],nextCursor:null,hasNext:false}],pageParams:[undefined],
    });
    expect(queryClient.getQueryData(notificationQueryKeys.list(true))).toEqual({
      pages:[{items:[notification],nextCursor:null,hasNext:false}],pageParams:[undefined],
    });
    expect(queryClient.getQueryData(notificationQueryKeys.unreadCount)).toBe(3);
    unmount();
  });

  it('같은 알림이 중복으로 오면 캐시와 콜백에 한 번만 반영한다',async()=>{
    const{queryClient,Wrapper}=createWrapper();
    queryClient.setQueryData(notificationQueryKeys.list(false),{pages:[{items:[],nextCursor:null,hasNext:false}],pageParams:[undefined]});
    queryClient.setQueryData(notificationQueryKeys.unreadCount,2);
    const onNotificationCreated=vi.fn();
    const{unmount}=renderHook(()=>useNotificationStream({onNotificationCreated}),{wrapper:Wrapper});
    await waitFor(()=>expect(EventSourceMock.instances).toHaveLength(1));

    act(()=>{
      publish(EventSourceMock.instances[0]!,notification);
      publish(EventSourceMock.instances[0]!,notification);
    });

    expect(onNotificationCreated).toHaveBeenCalledOnce();
    expect(queryClient.getQueryData(notificationQueryKeys.list(false))).toEqual({
      pages:[{items:[notification],nextCursor:null,hasNext:false}],pageParams:[undefined],
    });
    expect(queryClient.getQueryData(notificationQueryKeys.unreadCount)).toBe(3);
    unmount();
  });

  it('이미 읽은 알림이면 안읽음 카운트를 올리지 않는다',async()=>{
    const{queryClient,Wrapper}=createWrapper();
    queryClient.setQueryData(notificationQueryKeys.unreadCount,2);
    const{unmount}=renderHook(()=>useNotificationStream(),{wrapper:Wrapper});
    await waitFor(()=>expect(EventSourceMock.instances).toHaveLength(1));

    act(()=>publish(EventSourceMock.instances[0]!,{...notification,isRead:true}));

    expect(queryClient.getQueryData(notificationQueryKeys.unreadCount)).toBe(2);
    unmount();
  });

  it('잘못된 payload는 무시한다',async()=>{
    const{Wrapper}=createWrapper();
    const onNotificationCreated=vi.fn();
    const{unmount}=renderHook(()=>useNotificationStream({onNotificationCreated}),{wrapper:Wrapper});
    await waitFor(()=>expect(EventSourceMock.instances).toHaveLength(1));

    act(()=>publish(EventSourceMock.instances[0]!,'not-json'));

    expect(onNotificationCreated).not.toHaveBeenCalled();
    unmount();
  });

  it('연결 오류가 나면 고정 지연 후 재연결한다',async()=>{
    vi.useFakeTimers();
    const{Wrapper}=createWrapper();
    const{unmount}=renderHook(()=>useNotificationStream(),{wrapper:Wrapper});
    await vi.waitFor(()=>expect(EventSourceMock.instances).toHaveLength(1));

    EventSourceMock.instances[0]?.onerror?.();
    expect(EventSourceMock.instances).toHaveLength(1);

    await vi.advanceTimersByTimeAsync(2_000);

    expect(EventSourceMock.instances).toHaveLength(2);
    unmount();
    vi.useRealTimers();
  });

  it('enabled가 false면 연결하지 않는다',()=>{
    const{Wrapper}=createWrapper();
    const{unmount}=renderHook(()=>useNotificationStream({enabled:false}),{wrapper:Wrapper});

    expect(issueSseTicketMock).not.toHaveBeenCalled();
    expect(EventSourceMock.instances).toHaveLength(0);
    unmount();
  });

  it('로그인 상태가 아니면 티켓을 요청하지 않는다',async()=>{
    clearAccessToken();
    const{Wrapper}=createWrapper();
    const{unmount}=renderHook(()=>useNotificationStream(),{wrapper:Wrapper});

    await new Promise(resolve=>setTimeout(resolve,0));

    expect(issueSseTicketMock).not.toHaveBeenCalled();
    expect(EventSourceMock.instances).toHaveLength(0);
    unmount();
  });

  it('언마운트 시 연결을 정리한다',async()=>{
    const{Wrapper}=createWrapper();
    const{unmount}=renderHook(()=>useNotificationStream(),{wrapper:Wrapper});
    await waitFor(()=>expect(EventSourceMock.instances).toHaveLength(1));

    unmount();

    expect(EventSourceMock.instances[0]?.close).toHaveBeenCalledOnce();
  });
});
