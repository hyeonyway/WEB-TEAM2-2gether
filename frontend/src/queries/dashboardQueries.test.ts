import {describe,expect,it} from 'vitest';
import type {AuctionDto} from '../dto/auctionDto';
import type {AuctionStreamPayload} from '../hooks/useAuctionStream';
import {applyDashboardAuctionEvent} from './dashboardQueries';

const auction=(id:number,currentPrice:number,endsAt:string,eventId:number):AuctionDto=>({
  id,
  card:{
    id,
    name:`card-${id}`,
    marketPrice:currentPrice,
    lowPrice:currentPrice,
    highPrice:currentPrice,
    changeRate:0,
    theme:'gold',
    bidCount:1,
    psaGrade:'10',
    language:'KR',
    imageUrl:null,
  },
  startPrice:1_000,
  currentPrice,
  bidIncrement:1_000,
  bidCount:1,
  endsAt,
  status:'OPEN',
  eventId,
  myBidStatus:'OUTBID',
  myBidAmount:currentPrice-1_000,
});

const bidEvent:AuctionStreamPayload={
  type:'BID_PLACED',
  auction_id:1,
  bidder_id:2,
  previous_bidder_id:1,
  start_price:1_000,
  current_price:30_000,
  bid_increment:2_000,
  bid_count:3,
  ends_at:'2026-08-01T10:00:00Z',
  status:'ENDING',
  event_id:2,
  occurred_at:'2026-07-31T03:00:00Z',
};

describe('applyDashboardAuctionEvent',()=>{
  it('입찰 이벤트 값으로 캐시를 갱신하고 가격순을 다시 적용한다',()=>{
    const result=applyDashboardAuctionEvent([
      auction(1,10_000,'2026-08-01T10:00:00Z',1),
      auction(2,20_000,'2026-08-01T09:00:00Z',1),
    ],bidEvent,'PRICE_HIGH');

    expect(result.map(({id})=>id)).toEqual([1,2]);
    expect(result[0]).toMatchObject({
      currentPrice:30_000,
      bidCount:3,
      bidIncrement:2_000,
      status:'ENDING',
      version:2,
      card:{bidCount:3},
    });
  });

  it('이미 반영한 버전보다 오래된 이벤트는 값을 되돌리지 않는다',()=>{
    const result=applyDashboardAuctionEvent([
      auction(1,40_000,'2026-08-01T10:00:00Z',3),
    ],bidEvent,'ENDING_SOON');

    expect(result[0]).toMatchObject({
      currentPrice:40_000,
      bidCount:1,
      version:3,
    });
  });
});
