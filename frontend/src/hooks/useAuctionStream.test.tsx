import {act,renderHook} from '@testing-library/react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {useAuctionStream} from './useAuctionStream';

class EventSourceMock extends EventTarget{
  static instances:EventSourceMock[]=[];
  readonly url:string;
  close=vi.fn();
  onopen:((event:Event)=>void)|null=null;

  constructor(url:string|URL){
    super();
    this.url=String(url);
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

function publish(type:'AUCTION_CREATED'|'BID_PLACED'|'AUCTION_CLOSED'|'AUCTION_ENDING_STARTED',data:object|string){
  const eventSource=EventSourceMock.instances.at(-1);
  if(!eventSource)throw new Error('EventSource가 생성되지 않았습니다.');
  eventSource.dispatchEvent(new MessageEvent(type,{
    data:typeof data==='string'?data:JSON.stringify(data),
  }));
}

function openStream(){
  const eventSource=EventSourceMock.instances.at(-1);
  if(!eventSource)throw new Error('EventSource가 생성되지 않았습니다.');
  eventSource.onopen?.(new Event('open'));
}

describe('useAuctionStream',()=>{
  beforeEach(()=>{
    EventSourceMock.instances=[];
    vi.stubGlobal('EventSource',EventSourceMock);
  });

  afterEach(()=>vi.unstubAllGlobals());

  it.each([
    ['AUCTION_CREATED',{...basePayload,card_id:3,card_name:'리자몽',card_psa_grade:'10',card_language:'JP',card_thumbnail_url:null,seller_id:20}],
    ['BID_PLACED',{...basePayload,bidder_id:7,previous_bidder_id:5}],
    ['AUCTION_CLOSED',{...basePayload,final_price:50_000,card_id:3,card_name:'리자몽',card_psa_grade:'10',card_language:'JP',card_thumbnail_url:null,seller_id:20,winner_id:7}],
    ['AUCTION_ENDING_STARTED',{...basePayload,status:'ENDING'}],
  ] as const)('%s event 필드를 payload 타입으로 사용한다',(type,data)=>{
    const onAuctionUpdated=vi.fn();
    renderHook(()=>useAuctionStream({auctionIds:[10],onAuctionUpdated}));

    act(()=>publish(type,data));

    expect(onAuctionUpdated).toHaveBeenCalledWith(expect.objectContaining({type,auction_id:10}));
  });

  it('잘못된 data는 무시한다',()=>{
    const onAuctionUpdated=vi.fn();
    renderHook(()=>useAuctionStream({auctionIds:[10],onAuctionUpdated}));

    act(()=>publish('BID_PLACED','not-json'));
    act(()=>publish('BID_PLACED',{...basePayload}));

    expect(onAuctionUpdated).not.toHaveBeenCalled();
  });

  it('최초 연결을 제외하고 공유 연결이 재개되면 재연결 콜백을 한 번 실행한다',()=>{
    const onReconnected=vi.fn();
    renderHook(()=>useAuctionStream({auctionIds:[10],onAuctionUpdated:vi.fn(),onReconnected}));

    act(()=>openStream());
    expect(onReconnected).not.toHaveBeenCalled();

    act(()=>openStream());
    expect(onReconnected).toHaveBeenCalledOnce();
  });

  it('언마운트 시 이벤트 구독과 연결을 정리한다',()=>{
    const onAuctionUpdated=vi.fn();
    const {unmount}=renderHook(()=>useAuctionStream({auctionIds:[10],onAuctionUpdated}));
    const eventSource=EventSourceMock.instances[0];

    unmount();
    act(()=>publish('BID_PLACED',{...basePayload,bidder_id:7,previous_bidder_id:null}));

    expect(eventSource.close).toHaveBeenCalledOnce();
    expect(onAuctionUpdated).not.toHaveBeenCalled();
  });

  it('여러 훅이 하나의 EventSource 연결을 공유한다',()=>{
    const firstSubscriber=vi.fn();
    const secondSubscriber=vi.fn();
    const first=renderHook(()=>useAuctionStream({auctionIds:[10],onAuctionUpdated:firstSubscriber}));
    const second=renderHook(()=>useAuctionStream({auctionIds:[10],onAuctionUpdated:secondSubscriber}));
    const eventSource=EventSourceMock.instances[0];

    expect(EventSourceMock.instances).toHaveLength(1);
    first.unmount();
    expect(eventSource.close).not.toHaveBeenCalled();

    act(()=>publish('BID_PLACED',{...basePayload,bidder_id:7,previous_bidder_id:null}));

    expect(firstSubscriber).not.toHaveBeenCalled();
    expect(secondSubscriber).toHaveBeenCalledOnce();

    second.unmount();
    expect(eventSource.close).toHaveBeenCalledOnce();
  });

  it('활성 화면이 요청한 경매 ID 합집합으로 하나의 스트림을 연결한다',()=>{
    const first=renderHook(()=>useAuctionStream({auctionIds:[10,11],onAuctionUpdated:vi.fn()}));
    const second=renderHook(()=>useAuctionStream({auctionIds:[11,12],onAuctionUpdated:vi.fn()}));

    expect(EventSourceMock.instances).toHaveLength(2);
    expect(EventSourceMock.instances[1]?.url).toContain('/api/auctions/stream?auctionIds=10%2C11%2C12');
    expect(EventSourceMock.instances[0]?.close).toHaveBeenCalledOnce();

    first.unmount();
    second.unmount();
  });
});
