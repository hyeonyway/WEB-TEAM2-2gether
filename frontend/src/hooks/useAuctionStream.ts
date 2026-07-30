import {useEffect,useRef} from 'react';
import {isMockApiEnabled} from '../api/mockApiConfig';
import type {AuctionStatus} from '../dto/auctionDto';

export type AuctionStreamPayload={
  type:'AUCTION_CREATED'|'BID_PLACED'|'AUCTION_CLOSED';
  auction_id:number;
  card_id:number;
  card_name:string;
  card_psa_grade:string|null;
  card_language:string|null;
  card_thumbnail_url:string|null;
  seller_id:number;
  bidder_id?:number;
  previous_bidder_id?:number|null;
  winner_id?:number|null;
  start_price:number;
  current_price?:number;
  final_price?:number;
  bid_increment:number;
  bid_count:number;
  ends_at:string;
  status:AuctionStatus;
  auction_version:number;
  occurred_at:string;
};

type UseAuctionStreamOptions={
  enabled?:boolean;
  onAuctionUpdated:(payload:AuctionStreamPayload)=>void;
};

function auctionStreamUrl(){
  const apiBaseUrl=(import.meta.env.VITE_API_BASE_URL??'').replace(/\/+$/,'');
  return `${apiBaseUrl}/api/auctions/stream`;
}

function parsePayload(data:string):AuctionStreamPayload|null{
  try{
    const raw=JSON.parse(data) as Record<string,unknown>;
    const value={
      type:raw.type,
      auction_id:raw.auction_id??raw.auctionId,
      card_id:raw.card_id??raw.cardId,
      card_name:raw.card_name??raw.cardName,
      card_psa_grade:raw.card_psa_grade??raw.cardPsaGrade??null,
      card_language:raw.card_language??raw.cardLanguage??null,
      card_thumbnail_url:raw.card_thumbnail_url??raw.cardThumbnailUrl??null,
      seller_id:raw.seller_id??raw.sellerId,
      bidder_id:raw.bidder_id??raw.bidderId,
      previous_bidder_id:raw.previous_bidder_id??raw.previousBidderId??null,
      winner_id:raw.winner_id??raw.winnerId??null,
      start_price:raw.start_price??raw.startPrice,
      current_price:raw.current_price??raw.currentPrice,
      final_price:raw.final_price??raw.finalPrice,
      bid_increment:raw.bid_increment??raw.bidIncrement,
      bid_count:raw.bid_count??raw.bidCount,
      ends_at:raw.ends_at??raw.endsAt,
      status:raw.status,
      auction_version:raw.auction_version??raw.auctionVersion,
      occurred_at:raw.occurred_at??raw.occurredAt,
    } as Partial<AuctionStreamPayload>;
    if(
      !['AUCTION_CREATED','BID_PLACED','AUCTION_CLOSED'].includes(value.type??'')
      ||!Number.isInteger(value.auction_id)
      ||!Number.isInteger(value.card_id)
      ||typeof value.card_name!=='string'
      ||!Number.isFinite(value.start_price)
      ||!Number.isFinite(value.bid_increment)
      ||!Number.isInteger(value.bid_count)
      ||typeof value.ends_at!=='string'
      ||!Number.isFinite(value.auction_version)
      ||typeof value.occurred_at!=='string'
    )return null;
    return value as AuctionStreamPayload;
  }catch{
    return null;
  }
}

export function useAuctionStream({
  enabled=true,
  onAuctionUpdated,
}:UseAuctionStreamOptions){
  const onAuctionUpdatedRef=useRef(onAuctionUpdated);
  onAuctionUpdatedRef.current=onAuctionUpdated;

  useEffect(()=>{
    if(!enabled||isMockApiEnabled())return;

    const eventSource=new EventSource(auctionStreamUrl());
    const handleUpdate=(event:Event)=>{
      const payload=parsePayload((event as MessageEvent<string>).data);
      if(payload)onAuctionUpdatedRef.current(payload);
    };

    eventSource.addEventListener('auction-updated',handleUpdate);

    return ()=>{
      eventSource.removeEventListener('auction-updated',handleUpdate);
      eventSource.close();
    };
  },[enabled]);
}
