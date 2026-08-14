import {describe,expect,it} from 'vitest';
import type {AuctionDto,AuctionSort} from '../dto/auctionDto';
import type {AuctionStreamPayload} from '../hooks/useAuctionStream';
import {applyAuctionEvent,sortAuctions} from './auctionStreamCache';

const auction=(id:number,overrides:Partial<AuctionDto>={}):AuctionDto=>({
  id,
  card:{id,name:`card-${id}`,marketPrice:10_000,lowPrice:10_000,highPrice:10_000,changeRate:0,theme:'gold',bidCount:0,psaGrade:'10',language:'KR',imageUrl:null},
  startPrice:10_000,currentPrice:10_000,bidIncrement:1_000,bidCount:0,
  startsAt:'2026-08-04T10:00:00Z',endsAt:'2026-08-05T10:00:00Z',status:'OPEN',eventId:1,
  myBidStatus:'NONE',myBidAmount:null,
  ...overrides,
});

describe('sortAuctions',()=>{
  it('최신순은 시작 시각과 ID 내림차순으로 정렬한다',()=>{
    const result=sortAuctions([
      auction(3,{startsAt:'2026-08-03T10:00:00Z'}),
      auction(1,{startsAt:'2026-08-04T10:00:00Z'}),
      auction(2,{startsAt:'2026-08-04T10:00:00Z'}),
    ],'LATEST');

    expect(result.map(item=>item.id)).toEqual([2,1,3]);
  });

  it.each<AuctionSort>(['BID_COUNT','PRICE_HIGH','PRICE_LOW','CHANGE_HIGH','ENDING_SOON'])('%s 동률은 ID 내림차순으로 정렬한다',sort=>{
    expect(sortAuctions([auction(1),auction(3),auction(2)],sort).map(item=>item.id)).toEqual([3,2,1]);
  });

  it('마감 임박순은 ENDING 상태를 먼저 두고 종료 시각은 사용하지 않는다',()=>{
    const result=sortAuctions([
      auction(1,{status:'OPEN',endsAt:'2026-08-04T10:00:00Z'}),
      auction(2,{status:'ENDING',endsAt:'2026-08-05T10:00:00Z'}),
      auction(3,{status:'ENDING',endsAt:'2026-08-06T10:00:00Z'}),
    ],'ENDING_SOON',new Map([[3,0],[2,1]]));

    expect(result.map(item=>item.id)).toEqual([3,2,1]);
  });

  it('상승률순은 서버와 같은 basis point 단위로 비교한다',()=>{
    const result=sortAuctions([
      auction(2,{startPrice:100_000,currentPrice:100_001}),
      auction(1,{startPrice:100_000,currentPrice:100_009}),
    ],'CHANGE_HIGH');

    expect(result.map(item=>item.id)).toEqual([2,1]);
  });
});

describe('applyAuctionEvent',()=>{
  it('AUCTION_ENDING_STARTED 이벤트를 받으면 상태와 공개 마감시각을 갱신한다',()=>{
    const event={
      type:'AUCTION_ENDING_STARTED',auction_id:1,start_price:40_000,current_price:43_000,
      bid_increment:3_000,bid_count:2,ends_at:'2026-08-12T10:00:00.000Z',status:'ENDING',
      event_id:5,occurred_at:'2026-08-12T10:00:00.000Z',
    } as AuctionStreamPayload;

    const updated=applyAuctionEvent([auction(1)],event);

    expect(updated[0]).toMatchObject({
      currentPrice:43_000,bidIncrement:3_000,bidCount:2,
      status:'ENDING',endsAt:'2026-08-12T10:00:00.000Z',eventId:5,
    });
  });
});
