import {describe,expect,it} from 'vitest';
import type {AuctionDto,AuctionSort} from '../dto/auctionDto';
import {sortAuctions} from './auctionStreamCache';

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

  it.each<AuctionSort>(['BID_COUNT','PRICE_HIGH','PRICE_LOW','CHANGE_HIGH'])('%s 동률은 ID 내림차순으로 정렬한다',sort=>{
    expect(sortAuctions([auction(1),auction(3),auction(2)],sort).map(item=>item.id)).toEqual([3,2,1]);
  });

  it('상승률순은 서버와 같은 basis point 단위로 비교한다',()=>{
    const result=sortAuctions([
      auction(2,{startPrice:100_000,currentPrice:100_001}),
      auction(1,{startPrice:100_000,currentPrice:100_009}),
    ],'CHANGE_HIGH');

    expect(result.map(item=>item.id)).toEqual([2,1]);
  });
});
