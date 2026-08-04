import {mapCardLanguage,normalizePsaGrade,resolveImageUrl} from '../api/auctionMapper';
import type {AuctionDto,AuctionSort,MyBidStatus} from '../dto/auctionDto';
import type {AuctionStreamPayload} from '../hooks/useAuctionStream';

const themes:AuctionDto['card']['theme'][]=['gold','water','dark','multi','sketch'];

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
    if(auction.id!==event.auction_id||auction.version>=event.auction_version)return auction;
    return {
      ...auction,
      currentPrice:event.current_price??auction.currentPrice,
      bidCount:event.bid_count,
      bidIncrement:event.bid_increment,
      endsAt:event.ends_at,
      status:event.status,
      version:event.auction_version,
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
    endsAt:event.ends_at,
    status:event.status,
    version:event.auction_version,
    myBidStatus:'NONE',
    myBidAmount:null,
  };
}

export function sortAuctions(auctions:AuctionDto[],sort:AuctionSort):AuctionDto[]{
  return [...auctions].sort((left,right)=>{
    if(sort==='LATEST')return right.id-left.id;
    if(sort==='PRICE_HIGH')return right.currentPrice-left.currentPrice||right.id-left.id;
    if(sort==='PRICE_LOW')return left.currentPrice-right.currentPrice||right.id-left.id;
    if(sort==='CHANGE_HIGH'){
      const leftChange=(left.currentPrice-left.startPrice)/left.startPrice;
      const rightChange=(right.currentPrice-right.startPrice)/right.startPrice;
      return rightChange-leftChange||right.id-left.id;
    }
    return right.bidCount-left.bidCount||right.id-left.id;
  });
}
