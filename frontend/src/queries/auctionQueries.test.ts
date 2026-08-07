import {describe,expect,it} from 'vitest';
import type {BidContextResponseDto} from '../dto/auctionDto';
import type {AuctionStreamPayload} from '../hooks/useAuctionStream';
import {applyBidContextEvent,auctionQueryKeys} from './auctionQueries';

const bidEvent:AuctionStreamPayload={
  type:'BID_PLACED',auction_id:1,bidder_id:2,previous_bidder_id:7,start_price:1_000,current_price:30_000,bid_increment:2_000,bid_count:3,ends_at:'2026-08-04T11:00:00Z',status:'ENDING',event_id:2,occurred_at:'2026-08-03T06:00:00Z',
};

const bidContext:BidContextResponseDto={
  auction_id:1,status:'OPEN',current_price:10_000,minimum_bid:11_000,bid_increment:1_000,my_bid_status:'NONE',my_bid_amount:null,wallet:{available_balance:100_000,frozen_balance:0},recent_bids:[],
};

describe('auctionQueryKeys',()=>{
  const query={keyword:'',psaGrade:null,size:12,sort:'BID_COUNT' as const};

  it('공개 조회와 로그인 사용자 조회의 목록 캐시를 분리한다',()=>{
    expect(auctionQueryKeys.list(query,'public'))
      .not.toEqual(auctionQueryKeys.list(query,'self'));
  });

  it('공개 조회와 로그인 사용자 조회의 상세 캐시를 분리한다',()=>{
    expect(auctionQueryKeys.detail(1,'public'))
      .not.toEqual(auctionQueryKeys.detail(1,'self'));
  });

  it('입찰 SSE 이벤트를 열린 팝업의 입찰 컨텍스트에 반영한다',()=>{
    const result=applyBidContextEvent(bidContext,bidEvent);
    expect(result).toMatchObject({
      current_price:30_000,minimum_bid:32_000,bid_increment:2_000,status:'ENDING',eventId:2,
    });
    expect(result?.recent_bids[0]).toMatchObject({
      id:-2,amount:30_000,bidder_alias:'user-2***',is_highest:true,
    });
  });

  it('다른 사용자의 입찰 SSE가 오면 열린 팝업의 내 상태도 상회로 바꾼다',()=>{
    const leading={...bidContext,my_bid_status:'LEADING' as const,my_bid_amount:10_000};

    expect(applyBidContextEvent(leading,bidEvent)).toMatchObject({
      my_bid_status:'OUTBID',
      my_bid_amount:10_000,
      minimum_bid:32_000,
    });
  });

  it('오래된 SSE 이벤트는 팝업의 입찰 컨텍스트를 되돌리지 않는다',()=>{
    const current={...bidContext,eventId:3,current_price:40_000,minimum_bid:42_000};
    expect(applyBidContextEvent(current,bidEvent)).toEqual(current);
  });
});
