import {describe,expect,it} from 'vitest';
import {parseAuctionUpdatedEvent} from './auctionEventDto';

const snapshot={
  auction_id:10,card_id:20,card_name:'리자몽',card_psa_grade:'10',
  card_language:'JP',card_thumbnail_url:'/card.png',seller_id:3,
  start_price:40_000,bid_increment:1_000,bid_count:1,
  ends_at:'2026-07-30T13:00:00',status:'OPEN',auction_version:2,
  occurred_at:'2026-07-30T12:00:00',
} as const;

describe('parseAuctionUpdatedEvent',()=>{
  it.each([
    {...snapshot,type:'AUCTION_CREATED',current_price:40_000},
    {...snapshot,type:'BID_PLACED',bidder_id:7,previous_bidder_id:null,bid_price:50_000,current_price:50_000},
    {...snapshot,type:'AUCTION_CLOSED',winner_id:null,final_price:50_000,closed_at:'2026-07-30T13:00:00',status:'ENDED'},
  ])('$type payload를 파싱한다',payload=>{
    expect(parseAuctionUpdatedEvent(JSON.stringify(payload))).toEqual(payload);
  });

  it('필수 렌더링 필드가 누락되면 거부한다',()=>{
    const {card_name:_,...invalid}=snapshot;
    expect(parseAuctionUpdatedEvent(JSON.stringify({...invalid,type:'AUCTION_CREATED',current_price:40_000}))).toBeNull();
  });

  it('잘못된 nullable 타입을 거부한다',()=>{
    expect(parseAuctionUpdatedEvent(JSON.stringify({
      ...snapshot,type:'BID_PLACED',bidder_id:7,previous_bidder_id:'5',
      bid_price:50_000,current_price:50_000,
    }))).toBeNull();
  });
});
