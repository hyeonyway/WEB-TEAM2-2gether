import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {act,renderHook} from '@testing-library/react';
import type {ReactNode} from 'react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {walletQueryKeys} from '../queries/walletQueryKeys';
import {useAuctionWalletSync} from './useAuctionWalletSync';

class EventSourceMock extends EventTarget{
  static instances:EventSourceMock[]=[];
  close=vi.fn();

  constructor(_url:string|URL){
    super();
    EventSourceMock.instances.push(this);
  }
}

const basePayload={
  auction_id:10,
  start_price:40_000,
  current_price:50_000,
  bid_increment:1_000,
  bid_count:2,
  ends_at:'2026-08-03T15:30:00Z',
  status:'OPEN',
  auction_version:2,
  occurred_at:'2026-08-03T14:28:12Z',
};

function accessToken(userId:number){
  const payload=btoa(JSON.stringify({sub:String(userId)}))
    .replaceAll('+','-')
    .replaceAll('/','_')
    .replaceAll('=','');
  return `header.${payload}.signature`;
}

function publish(type:'BID_PLACED'|'AUCTION_CLOSED',data:object){
  const eventSource=EventSourceMock.instances.at(-1);
  if(!eventSource)throw new Error('EventSource가 생성되지 않았습니다.');
  eventSource.dispatchEvent(new MessageEvent(type,{data:JSON.stringify(data)}));
}

function setup(userId=7){
  const queryClient=new QueryClient({
    defaultOptions:{queries:{retry:false}},
  });
  queryClient.setQueryData(walletQueryKeys.balance(),{
    totalBalance:100_000,
    frozenBalance:20_000,
    availableBalance:80_000,
  });
  const wrapper=({children}:{children:ReactNode})=><QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  renderHook(()=>useAuctionWalletSync(accessToken(userId),true),{wrapper});
  return queryClient;
}

function expectWalletInvalidated(queryClient:QueryClient,expected:boolean){
  expect(queryClient.getQueryState(walletQueryKeys.balance())?.isInvalidated).toBe(expected);
}

describe('useAuctionWalletSync',()=>{
  beforeEach(()=>{
    EventSourceMock.instances=[];
    vi.stubGlobal('EventSource',EventSourceMock);
  });

  afterEach(()=>vi.unstubAllGlobals());

  it('현재 사용자가 새 최고 입찰자이면 Wallet Query를 무효화한다',()=>{
    const queryClient=setup(7);

    act(()=>publish('BID_PLACED',{...basePayload,bidder_id:7,previous_bidder_id:5}));

    expectWalletInvalidated(queryClient,true);
  });

  it('현재 사용자가 상회 입찰당하면 해제된 hold를 다시 조회한다',()=>{
    const queryClient=setup(5);

    act(()=>publish('BID_PLACED',{...basePayload,bidder_id:7,previous_bidder_id:5}));

    expectWalletInvalidated(queryClient,true);
  });

  it('현재 사용자가 낙찰되면 차감된 Wallet을 다시 조회한다',()=>{
    const queryClient=setup(7);

    act(()=>publish('AUCTION_CLOSED',{
      ...basePayload,
      final_price:50_000,
      card_id:3,
      card_name:'리자몽',
      card_psa_grade:'10',
      card_language:'JP',
      card_thumbnail_url:null,
      seller_id:20,
      winner_id:7,
    }));

    expectWalletInvalidated(queryClient,true);
  });

  it('Wallet이 변하지 않는 다른 사용자의 이벤트는 무시한다',()=>{
    const queryClient=setup(9);

    act(()=>publish('BID_PLACED',{...basePayload,bidder_id:7,previous_bidder_id:5}));

    expectWalletInvalidated(queryClient,false);
  });
});
