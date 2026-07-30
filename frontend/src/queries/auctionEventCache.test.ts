import {describe,expect,it} from 'vitest';
import type {AuctionDto,AuctionListRequestDto} from '../dto/auctionDto';
import type {AuctionClosedEventDto,AuctionCreatedEventDto,BidPlacedEventDto} from '../dto/auctionEventDto';
import {auctionFromEvent,updateAuctionList,updateParticipatingAuctions,updateRecentWins} from './auctionEventCache';

const created:AuctionCreatedEventDto={
  type:'AUCTION_CREATED',auction_id:10,card_id:20,card_name:'리자몽',
  card_psa_grade:'10',card_language:'JP',card_thumbnail_url:'/card.png',
  seller_id:3,start_price:40_000,current_price:40_000,bid_increment:1_000,
  bid_count:0,ends_at:'2026-07-30T13:00:00',status:'OPEN',
  auction_version:1,occurred_at:'2026-07-30T12:00:00',
};
const bid:BidPlacedEventDto={
  ...created,type:'BID_PLACED',bidder_id:7,previous_bidder_id:5,
  bid_price:50_000,current_price:50_000,bid_count:1,auction_version:2,
};
const closed:AuctionClosedEventDto={
  ...created,type:'AUCTION_CLOSED',winner_id:7,final_price:50_000,
  bid_count:1,status:'ENDED',auction_version:3,
  closed_at:'2026-07-30T13:00:00',
};
const request=(sort:AuctionListRequestDto['sort']='BID_COUNT'):AuctionListRequestDto=>({
  keyword:'',psaGrade:null,sort,
});

describe('auction event cache',()=>{
  it('생성 이벤트를 필터에 맞는 목록에 추가하고 정렬한다',()=>{
    const other={...auctionFromEvent({...created,auction_id:11,card_id:21}),bidCount:3};
    expect(updateAuctionList([other],request(),created)?.map(item=>item.id)).toEqual([11,10]);
    expect(updateAuctionList([],{...request(),keyword:'피카츄'},created)).toEqual([]);
  });

  it('입찰 이벤트로 가격과 입찰 수를 갱신하고 낮은 버전은 무시한다',()=>{
    const updated=updateAuctionList([auctionFromEvent(created)],request('PRICE_HIGH'),bid);
    expect(updated?.[0]).toMatchObject({currentPrice:50_000,bidCount:1,version:2});
    const stale={...bid,current_price:45_000,auction_version:1};
    expect(updateAuctionList(updated,request('PRICE_HIGH'),stale)?.[0].currentPrice).toBe(50_000);
  });

  it('입찰자와 이전 최고 입찰자의 대시보드 상태를 갱신한다',()=>{
    expect(updateParticipatingAuctions([],'ENDING_SOON',bid,7)?.[0])
      .toMatchObject({myBidStatus:'LEADING',myBidAmount:50_000});
    const previous:AuctionDto={...auctionFromEvent(created,'LEADING',45_000),version:1};
    expect(updateParticipatingAuctions([previous],'ENDING_SOON',bid,5)?.[0])
      .toMatchObject({myBidStatus:'OUTBID',myBidAmount:45_000});
  });

  it('종료 시 참여 목록에서 제거하고 낙찰자의 최근 낙찰에 추가한다',()=>{
    const participating=[auctionFromEvent(bid,'LEADING',50_000)];
    expect(updateParticipatingAuctions(participating,'ENDING_SOON',closed,7)).toEqual([]);
    expect(updateRecentWins([],'LATEST',closed,7)?.[0])
      .toMatchObject({id:10,status:'ENDED',currentPrice:50_000});
    expect(updateRecentWins([],'LATEST',closed,5)).toEqual([]);
  });

  it('낮은 버전의 종료 이벤트로 최신 항목을 제거하지 않는다',()=>{
    const current={...auctionFromEvent(bid),version:4};
    expect(updateAuctionList([current],request(),closed)).toEqual([current]);
    expect(updateParticipatingAuctions([current],'ENDING_SOON',closed,7)).toEqual([current]);
  });
});
