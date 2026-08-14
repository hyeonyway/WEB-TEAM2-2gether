import {mapCardLanguage,normalizePsaGrade,resolveImageUrl} from '../api/auctionMapper';
import type {AuctionDto,AuctionSort,MyBidStatus} from '../dto/auctionDto';
import type {AuctionStreamPayload} from '../hooks/useAuctionStream';

const themes:AuctionDto['card']['theme'][]=['gold','water','dark','multi','sketch'];

const changeRateBasisPoints=(auction:AuctionDto)=>Math.floor(
  (auction.currentPrice-auction.startPrice)*10_000/auction.startPrice,
);

export function myBidStatusAfterEvent(
  current:MyBidStatus,
  event:AuctionStreamPayload,
):MyBidStatus{
  const outbidByAnotherUser=event.type==='BID_PLACED'
    &&event.previous_bidder_id!==null
    &&event.previous_bidder_id!==event.bidder_id;
  return current==='LEADING'&&outbidByAnotherUser?'OUTBID':current;
}

export function applyAuctionEvent(
  auctions:AuctionDto[]|undefined,
  event:AuctionStreamPayload,
):AuctionDto[]{
  if(!auctions)return [];
  if(event.type==='AUCTION_CLOSED'){
    return auctions.filter(auction=>auction.id!==event.auction_id);
  }
  return auctions.map(auction=>{
    if(auction.id!==event.auction_id||(auction.eventId??0)>=event.event_id)return auction;
    return {
      ...auction,
      currentPrice:event.current_price??auction.currentPrice,
      bidCount:event.bid_count,
      bidIncrement:event.bid_increment,
      endsAt:event.ends_at,
      status:event.status,
      eventId:event.event_id,
      myBidStatus:myBidStatusAfterEvent(auction.myBidStatus,event),
      card:{...auction.card,bidCount:event.bid_count},
    };
  });
}

export function eventToAuction(event:AuctionStreamPayload):AuctionDto{
  if(event.type!=='AUCTION_CREATED'){
    throw new Error('AUCTION_CREATED 이벤트만 경매 DTO로 변환할 수 있습니다.');
  }
  const currentPrice=event.current_price??event.final_price??event.start_price;
  return {
    id:event.auction_id,
    sellerId:event.seller_id,
    card:{
      id:event.card_id,
      name:event.card_name,
      marketPrice:currentPrice,
      lowPrice:currentPrice,
      highPrice:currentPrice,
      changeRate:0,
      theme:themes[event.card_id%themes.length],
      bidCount:event.bid_count,
      psaGrade:normalizePsaGrade(event.card_psa_grade),
      language:mapCardLanguage(event.card_language),
      imageUrl:resolveImageUrl(event.card_thumbnail_url),
    },
    startPrice:event.start_price,
    currentPrice,
    bidIncrement:event.bid_increment,
    bidCount:event.bid_count,
    startsAt:event.occurred_at,
    endsAt:event.ends_at,
    status:event.status,
    eventId:event.event_id,
    myBidStatus:'NONE',
    myBidAmount:null,
  };
}

export function sortAuctions(auctions:AuctionDto[],sort:AuctionSort,endingRanks:ReadonlyMap<number,number>=new Map()):AuctionDto[]{
  return [...auctions].sort((left,right)=>{
    if(sort==='LATEST'){
      const timeOrder=Date.parse(right.startsAt??'')-Date.parse(left.startsAt??'');
      return (Number.isNaN(timeOrder)?0:timeOrder)||right.id-left.id;
    }
    if(sort==='PRICE_HIGH')return right.currentPrice-left.currentPrice||right.id-left.id;
    if(sort==='PRICE_LOW')return left.currentPrice-right.currentPrice||right.id-left.id;
    if(sort==='CHANGE_HIGH'){
      const leftChange=changeRateBasisPoints(left);
      const rightChange=changeRateBasisPoints(right);
      return rightChange-leftChange||right.id-left.id;
    }
    if(sort==='ENDING_SOON'){
      const leftEnding=left.status==='ENDING',rightEnding=right.status==='ENDING';
      if(leftEnding!==rightEnding)return leftEnding?-1:1;
      if(leftEnding)return (endingRanks.get(left.id)??0)-(endingRanks.get(right.id)??0);
      return right.id-left.id;
    }
    return right.bidCount-left.bidCount||right.id-left.id;
  });
}
