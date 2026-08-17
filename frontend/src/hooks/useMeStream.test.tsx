import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {act,renderHook} from '@testing-library/react';
import type {ReactNode} from 'react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {fetchWalletBalance} from '../api/walletApi';
import {getSessionUserId,setSession} from '../auth/session/sessionAuthStore';
import {notificationQueryKeys} from '../queries/notificationQueries';
import {walletQueryKeys} from '../queries/walletQueryKeys';
import {useMeStream} from './useMeStream';

vi.mock('../api/walletApi',()=>({fetchWalletBalance:vi.fn()}));

class EventSourceMock extends EventTarget{
  static instances:EventSourceMock[]=[];
  close=vi.fn();
  onopen:((event:Event)=>void)|null=null;
  onerror:((event:Event)=>void)|null=null;
  constructor(public url:string|URL){super();EventSourceMock.instances.push(this);}
}

const fetchWalletBalanceMock=vi.mocked(fetchWalletBalance);

function walletPayload(version:number,totalBalance:number){return JSON.stringify({
  wallet_version:version,total_balance:totalBalance,frozen_balance:1_000,
  available_balance:totalBalance-1_000,updated_at:'2026-08-12T00:00:00Z',
});}

function notificationPayload(id:number,isRead=false){return JSON.stringify({
  id,auctionId:100,bidId:0,message:'메시지',isRead,createdAt:'2026-07-30T12:00:00Z',
});}

function wrapper(queryClient:QueryClient){
  return ({children}:{children:ReactNode})=><QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe('useMeStream',()=>{
  beforeEach(()=>{
    EventSourceMock.instances=[];
    vi.stubGlobal('EventSource',EventSourceMock);
    vi.useFakeTimers();
    setSession(1);
  });
  afterEach(()=>{vi.useRealTimers();vi.unstubAllGlobals();vi.clearAllMocks();setSession(null);});

  it('세션이 있으면 /api/me/stream 하나에 연결한다',async()=>{
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    renderHook(()=>useMeStream(),{wrapper:wrapper(queryClient)});
    await act(async()=>{await Promise.resolve();});

    expect(EventSourceMock.instances).toHaveLength(1);
    expect(String(EventSourceMock.instances[0]?.url)).toContain('/api/me/stream');
  });

  it('지갑 이벤트를 받으면 지갑 캐시를 갱신한다',async()=>{
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    renderHook(()=>useMeStream(),{wrapper:wrapper(queryClient)});
    await act(async()=>{await Promise.resolve();});
    const source=EventSourceMock.instances[0];

    act(()=>source?.dispatchEvent(new MessageEvent('wallet-state-changed',{data:walletPayload(5,10_000)})));

    expect(queryClient.getQueryData(walletQueryKeys.balance())).toEqual({
      totalBalance:10_000,frozenBalance:1_000,availableBalance:9_000,walletVersion:5,
    });
  });

  it('알림 이벤트를 받으면 안읽음 카운트를 올리고 콜백을 호출한다',async()=>{
    const onNotificationCreated=vi.fn();
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    queryClient.setQueryData(notificationQueryKeys.unreadCount,0);
    renderHook(()=>useMeStream({onNotificationCreated}),{wrapper:wrapper(queryClient)});
    await act(async()=>{await Promise.resolve();});
    const source=EventSourceMock.instances[0];

    act(()=>source?.dispatchEvent(new MessageEvent('notification-created',{data:notificationPayload(42)})));

    expect(queryClient.getQueryData(notificationQueryKeys.unreadCount)).toBe(1);
    expect(onNotificationCreated).toHaveBeenCalledOnce();
  });

  it('재연결되면 지갑 재조회와 알림 캐시 무효화를 모두 수행한다',async()=>{
    fetchWalletBalanceMock.mockResolvedValueOnce({
      totalBalance:5_000,frozenBalance:0,availableBalance:5_000,walletVersion:1,
    });
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    const invalidateSpy=vi.spyOn(queryClient,'invalidateQueries');
    renderHook(()=>useMeStream(),{wrapper:wrapper(queryClient)});
    await act(async()=>{await Promise.resolve();});
    const first=EventSourceMock.instances[0];
    act(()=>first?.onopen?.(new Event('open')));
    act(()=>first?.onerror?.(new Event('error')));
    await act(async()=>{await vi.advanceTimersByTimeAsync(2_000);});
    const second=EventSourceMock.instances[1];

    await act(async()=>{second?.onopen?.(new Event('open'));await Promise.resolve();});

    expect(fetchWalletBalanceMock).toHaveBeenCalledOnce();
    expect(invalidateSpy).toHaveBeenCalledWith({queryKey:notificationQueryKeys.all});
  });

  it('연속 실패할수록 재연결 지연이 지수적으로 늘어난다',async()=>{
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    renderHook(()=>useMeStream(),{wrapper:wrapper(queryClient)});
    await act(async()=>{await Promise.resolve();});

    act(()=>EventSourceMock.instances[0]?.onerror?.(new Event('error')));
    await act(async()=>{await vi.advanceTimersByTimeAsync(2_000);});
    expect(EventSourceMock.instances).toHaveLength(2);

    act(()=>EventSourceMock.instances[1]?.onerror?.(new Event('error')));
    await act(async()=>{await vi.advanceTimersByTimeAsync(2_000);});
    expect(EventSourceMock.instances).toHaveLength(2);
    await act(async()=>{await vi.advanceTimersByTimeAsync(2_000);});
    expect(EventSourceMock.instances).toHaveLength(3);
  });

  it('연속 5회 실패하면 세션을 재검증하고, 만료된 상태면 로그인 상태를 비운다',async()=>{
    const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(
      new Response(JSON.stringify({code:'SESSION_EXPIRED'}),{status:401,headers:{'Content-Type':'application/json'}}),
    );
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    renderHook(()=>useMeStream(),{wrapper:wrapper(queryClient)});
    await act(async()=>{await Promise.resolve();});

    let delay=2_000;
    for(let i=0;i<5;i++){
      const last=EventSourceMock.instances[EventSourceMock.instances.length-1];
      act(()=>last?.onerror?.(new Event('error')));
      await act(async()=>{await vi.advanceTimersByTimeAsync(delay);});
      delay=Math.min(delay*2,30_000);
    }

    await vi.waitFor(()=>expect(fetchMock).toHaveBeenCalled());
    expect(fetchMock.mock.calls.some(([path])=>path==='/api/auth/me')).toBe(true);
    expect(getSessionUserId()).toBeNull();
  });

  it('enabled가 false면 연결하지 않는다',()=>{
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    renderHook(()=>useMeStream({enabled:false}),{wrapper:wrapper(queryClient)});

    expect(EventSourceMock.instances).toHaveLength(0);
  });

  it('로그인 상태가 아니면 스트림에 연결하지 않는다',async()=>{
    setSession(null);
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    renderHook(()=>useMeStream(),{wrapper:wrapper(queryClient)});
    await act(async()=>{await Promise.resolve();});

    expect(EventSourceMock.instances).toHaveLength(0);
  });

  it('언마운트 시 연결을 정리한다',async()=>{
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    const {unmount}=renderHook(()=>useMeStream(),{wrapper:wrapper(queryClient)});
    await act(async()=>{await Promise.resolve();});
    const source=EventSourceMock.instances[0];

    unmount();

    expect(source?.close).toHaveBeenCalled();
  });
});
