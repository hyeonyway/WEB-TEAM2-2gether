import {describe,expect,it} from 'vitest';
import {mapAuction} from './auctionMapper';
import type {AuctionResponseDto} from '../dto/auctionDto';

const auctionResponse:AuctionResponseDto={
  id:1,
  card:{
    id:47,
    name:'메가 망나뇽 ex',
    set_name:'Mock Set',
    psa_grade:'PSA 10',
    language:'Japanese',
    thumbnail_url:'pokemon-cards/card.webp',
  },
  seller:{id:2,nickname:'seller',trade_count:0,trust_score:0},
  start_price:100000,
  current_price:120000,
  bid_increment:1000,
  minimum_bid:121000,
  bid_count:3,
  starts_at:'2026-07-30T00:00:00',
  ends_at:'2026-07-31T12:00:00',
  status:'OPEN',
  my_bid_status:'NONE',
  my_bid_amount:null,
  version:1,
};

describe('mapAuction',()=>{
  it.each([
    ['Japanese','JP'],
    ['English','EN'],
    ['Korean','KR'],
  ] as const)('백엔드 언어 %s를 화면 코드 %s로 변환한다',(language,expected)=>{
    const auction=mapAuction({
      ...auctionResponse,
      card:{...auctionResponse.card,language},
    });

    expect(auction.card.language).toBe(expected);
  });
});
