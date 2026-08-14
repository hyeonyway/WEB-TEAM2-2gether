import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {act,renderHook} from '@testing-library/react';
import type {ReactNode} from 'react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {fetchWalletBalance} from '../api/walletApi';
import {getSessionUserId,setSessionUserId} from '../auth/session/sessionAuthStore';
import {walletQueryKeys} from '../queries/walletQueryKeys';
import {useWalletStream} from './useWalletStream';

vi.mock('../api/walletApi',()=>({fetchWalletBalance:vi.fn()}));

class EventSourceMock extends EventTarget{
  static instances:EventSourceMock[]=[];
  close=vi.fn();
  onopen:((event:Event)=>void)|null=null;
  onerror:((event:Event)=>void)|null=null;
  constructor(public url:string|URL){super();EventSourceMock.instances.push(this);}
}

const fetchWalletBalanceMock=vi.mocked(fetchWalletBalance);

function payload(version:number,totalBalance:number){return JSON.stringify({
  wallet_version:version,total_balance:totalBalance,frozen_balance:1_000,
  available_balance:totalBalance-1_000,updated_at:'2026-08-12T00:00:00Z',
});}

describe('useWalletStream',()=>{
  beforeEach(()=>{EventSourceMock.instances=[];vi.stubGlobal('EventSource',EventSourceMock);vi.useFakeTimers();});
  afterEach(()=>{vi.useRealTimers();vi.unstubAllGlobals();vi.clearAllMocks();setSessionUserId(null);});

  it('재연결 REST 응답은 그 사이 받은 더 최신 SSE snapshot을 덮어쓰지 않는다',async()=>{
    let resolveRecovery!:(value:{totalBalance:number;frozenBalance:number;availableBalance:number})=>void;
    fetchWalletBalanceMock.mockReturnValueOnce(new Promise(resolve=>{resolveRecovery=resolve;}));
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    const wrapper=({children}:{children:ReactNode})=><QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
    renderHook(()=>useWalletStream(true),{wrapper});
    await act(async()=>{await Promise.resolve();});
    const first=EventSourceMock.instances[0];
    act(()=>first?.onopen?.(new Event('open')));
    act(()=>first?.onerror?.(new Event('error')));
    await act(async()=>{await vi.advanceTimersByTimeAsync(2_000);});
    const second=EventSourceMock.instances[1];
    act(()=>second?.onopen?.(new Event('open')));
    expect(fetchWalletBalanceMock).toHaveBeenCalledOnce();
    act(()=>second?.dispatchEvent(new MessageEvent('wallet-state-changed',{data:payload(8,20_000)})));
    await act(async()=>{resolveRecovery({totalBalance:10_000,frozenBalance:1_000,availableBalance:9_000});});

    expect(queryClient.getQueryData(walletQueryKeys.balance())).toEqual({
      totalBalance:20_000,frozenBalance:1_000,availableBalance:19_000,walletVersion:8,
    });
  });

  it('재연결 REST snapshot보다 오래된 재전달 SSE는 적용하지 않는다',async()=>{
    fetchWalletBalanceMock.mockResolvedValueOnce({
      totalBalance:17_000,frozenBalance:1_000,availableBalance:16_000,walletVersion:7,
    });
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    const wrapper=({children}:{children:ReactNode})=><QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
    renderHook(()=>useWalletStream(true),{wrapper});
    await act(async()=>{await Promise.resolve();});
    const first=EventSourceMock.instances[0];
    act(()=>first?.onopen?.(new Event('open')));
    act(()=>first?.onerror?.(new Event('error')));
    await act(async()=>{await vi.advanceTimersByTimeAsync(2_000);});
    const second=EventSourceMock.instances[1];
    await act(async()=>{second?.onopen?.(new Event('open'));await Promise.resolve();});
    act(()=>second?.dispatchEvent(new MessageEvent('wallet-state-changed',{data:payload(6,16_000)})));

    expect(queryClient.getQueryData(walletQueryKeys.balance())).toEqual({
      totalBalance:17_000,frozenBalance:1_000,availableBalance:16_000,walletVersion:7,
    });
  });

  it('연속 실패할수록 재연결 지연이 지수적으로 늘어난다',async()=>{
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    const wrapper=({children}:{children:ReactNode})=><QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
    renderHook(()=>useWalletStream(true),{wrapper});
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
    setSessionUserId(37);
    const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(
      new Response(JSON.stringify({code:'SESSION_EXPIRED'}),{status:401,headers:{'Content-Type':'application/json'}}),
    );
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    const wrapper=({children}:{children:ReactNode})=><QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
    renderHook(()=>useWalletStream(true),{wrapper});
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
});
