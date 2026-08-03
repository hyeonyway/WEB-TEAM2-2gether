import {describe,expect,it} from 'vitest';
import type {AuctionDto,BidContextResponseDto,PageResponseDto} from '../dto/auctionDto';
import type {AuctionStreamPayload} from '../hooks/useAuctionStream';
import {applyAuctionListEvent,applyBidContextEvent,auctionQueryKeys} from './auctionQueries';

const auction=(id:number,currentPrice:number,version=1):AuctionDto=>({
  id,card:{id,name:`card-${id}`,marketPrice:currentPrice,lowPrice:currentPrice,highPrice:currentPrice,changeRate:0,theme:'gold',bidCount:1,psaGrade:'10',language:'KR',imageUrl:null},
  startPrice:1_000,currentPrice,bidIncrement:1_000,bidCount:1,endsAt:'2026-08-04T10:00:00Z',status:'OPEN',version,myBidStatus:'NONE',myBidAmount:null,
});

const page=(content:AuctionDto[]):PageResponseDto<AuctionDto>=>({content,page:0,size:12,total_elements:content.length,has_next:false});

const bidEvent:AuctionStreamPayload={
  type:'BID_PLACED',auction_id:1,bidder_id:2,previous_bidder_id:null,start_price:1_000,current_price:30_000,bid_increment:2_000,bid_count:3,ends_at:'2026-08-04T11:00:00Z',status:'ENDING',auction_version:2,occurred_at:'2026-08-03T06:00:00Z',
};

const createdEvent:AuctionStreamPayload={
  type:'AUCTION_CREATED',auction_id:3,seller_id:7,card_id:3,card_name:'새 카드',card_psa_grade:'PSA 10',card_language:'KR',card_thumbnail_url:null,start_price:25_000,current_price:25_000,bid_increment:1_000,bid_count:0,ends_at:'2026-08-05T11:00:00Z',status:'OPEN',auction_version:1,occurred_at:'2026-08-03T06:00:00Z',
};

const bidContext:BidContextResponseDto={
  auction_id:1,status:'OPEN',version:1,current_price:10_000,minimum_bid:11_000,bid_increment:1_000,my_bid_status:'NONE',my_bid_amount:null,wallet:{available_balance:100_000,frozen_balance:0},recent_bids:[],
};

describe('auctionQueryKeys',()=>{
  const query={keyword:'',psaGrade:null,page:0,size:12,sort:'BID_COUNT' as const};

  it('공개 조회와 로그인 사용자 조회의 목록 캐시를 분리한다',()=>{
    expect(auctionQueryKeys.list(query,'public'))
      .not.toEqual(auctionQueryKeys.list(query,'self'));
  });

  it('공개 조회와 로그인 사용자 조회의 상세 캐시를 분리한다',()=>{
    expect(auctionQueryKeys.detail(1,'public'))
      .not.toEqual(auctionQueryKeys.detail(1,'self'));
  });

  it('입찰 SSE 이벤트를 목록 캐시에 반영하고 현재 정렬을 다시 적용한다',()=>{
    const result=applyAuctionListEvent(page([auction(1,10_000),auction(2,20_000)]),bidEvent,query);

    expect(result?.content.map(item=>item.id)).toEqual([1,2]);
    expect(result?.content[0]).toMatchObject({currentPrice:30_000,bidCount:3,bidIncrement:2_000,status:'ENDING',version:2});
  });

  it('현재 캐시보다 오래된 SSE 이벤트는 값을 되돌리지 않는다',()=>{
    const result=applyAuctionListEvent(page([auction(1,40_000,3)]),bidEvent,query);

    expect(result?.content[0]).toMatchObject({currentPrice:40_000,bidCount:1,version:3});
  });

  it('조건에 맞는 신규 경매를 첫 페이지에 추가한다',()=>{
    const result=applyAuctionListEvent(page([auction(1,10_000)]),createdEvent,query);

    expect(result?.total_elements).toBe(2);
    expect(result?.content.map(item=>item.id)).toEqual([1,3]);
  });

  it('검색 조건에 맞지 않는 신규 경매는 현재 목록에 추가하지 않는다',()=>{
    const result=applyAuctionListEvent(page([auction(1,10_000)]),createdEvent,{...query,keyword:'피카츄'});

    expect(result).toEqual(page([auction(1,10_000)]));
  });

  it('입찰 SSE 이벤트를 열린 팝업의 입찰 컨텍스트에 반영한다',()=>{
    const result=applyBidContextEvent(bidContext,bidEvent);
    expect(result).toMatchObject({
      current_price:30_000,minimum_bid:32_000,bid_increment:2_000,status:'ENDING',version:2,
    });
    expect(result?.recent_bids[0]).toMatchObject({
      id:-2,amount:30_000,bidder_alias:'user-2***',is_highest:true,
    });
  });

  it('오래된 SSE 이벤트는 팝업의 입찰 컨텍스트를 되돌리지 않는다',()=>{
    const current={...bidContext,version:3,current_price:40_000,minimum_bid:42_000};
    expect(applyBidContextEvent(current,bidEvent)).toEqual(current);
  });
});
