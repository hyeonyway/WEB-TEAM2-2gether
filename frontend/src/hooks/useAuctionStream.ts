import {useEffect,useRef} from 'react';
import {isMockApiEnabled} from '../api/mockApiConfig';
import type {AuctionStatus} from '../dto/auctionDto';

type AuctionStreamBase={
  auction_id:number;
  start_price:number;
  current_price?:number;
  final_price?:number;
  bid_increment:number;
  bid_count:number;
  ends_at:string;
  status:AuctionStatus;
  event_id:number;
  occurred_at:string;
};

type AuctionCardSnapshot={
  card_id:number;
  card_name:string;
  card_psa_grade:string|null;
  card_language:string|null;
  card_thumbnail_url:string|null;
};

export type AuctionStreamPayload=
  |AuctionStreamBase&AuctionCardSnapshot&{
    type:'AUCTION_CREATED';
    seller_id:number;
  }
  |AuctionStreamBase&{
    type:'BID_PLACED';
    bidder_id:number;
    previous_bidder_id:number|null;
  }
  |AuctionStreamBase&AuctionCardSnapshot&{
    type:'AUCTION_CLOSED';
    seller_id:number;
    winner_id:number|null;
  };

const AUCTION_STREAM_EVENT_TYPES=['AUCTION_CREATED','BID_PLACED','AUCTION_CLOSED'] as const;
type AuctionStreamEventType=typeof AUCTION_STREAM_EVENT_TYPES[number];

type UseAuctionStreamOptions={
  auctionIds:readonly number[];
  enabled?:boolean;
  onAuctionUpdated:(payload:AuctionStreamPayload)=>void;
  onReconnected?:()=>void;
};

type AuctionStreamSubscriber={
  auctionIds:ReadonlySet<number>;
  onAuctionUpdated:(payload:AuctionStreamPayload)=>void;
  onReconnected?:()=>void;
};

const subscribers=new Set<AuctionStreamSubscriber>();
let sharedEventSource:EventSource|null=null;
let sharedListeners:ReadonlyArray<readonly[string,EventListener]>=[];
let hasOpenedSharedEventSource=false;
let sharedSubscriptionSignature='';

function auctionStreamUrl(auctionIds:readonly number[]){
  const apiBaseUrl=(import.meta.env.VITE_API_BASE_URL??'').replace(/\/+$/,'');
  return `${apiBaseUrl}/api/auctions/stream?${new URLSearchParams({auctionIds:auctionIds.join(',')}).toString()}`;
}

function parsePayload(type:AuctionStreamEventType,event:MessageEvent<string>):AuctionStreamPayload|null{
  try{
    const raw=JSON.parse(event.data) as Record<string,unknown>;
    const value={
      type,
      auction_id:raw.auction_id??raw.auctionId,
      ...(raw.card_id!==undefined||raw.cardId!==undefined?{
        card_id:raw.card_id??raw.cardId,
        card_name:raw.card_name??raw.cardName,
        card_psa_grade:raw.card_psa_grade??raw.cardPsaGrade??null,
        card_language:raw.card_language??raw.cardLanguage??null,
        card_thumbnail_url:raw.card_thumbnail_url??raw.cardThumbnailUrl??null,
      }:{}),
      ...(raw.seller_id!==undefined||raw.sellerId!==undefined?{
        seller_id:raw.seller_id??raw.sellerId,
      }:{}),
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
      event_id:Number(event.lastEventId),
      occurred_at:raw.occurred_at??raw.occurredAt,
    } as Partial<AuctionStreamPayload>;
    if(
      !Number.isInteger(value.auction_id)
      ||!Number.isFinite(value.start_price)
      ||!Number.isFinite(value.bid_increment)
      ||!Number.isInteger(value.bid_count)
      ||typeof value.ends_at!=='string'
      ||!Number.isFinite(value.event_id)
      ||typeof value.occurred_at!=='string'
    )return null;
    if(value.type!=='BID_PLACED'&&(
      !Number.isInteger(value.card_id)
      ||typeof value.card_name!=='string'
      ||!Number.isInteger(value.seller_id)
    ))return null;
    if(value.type==='BID_PLACED'&&!Number.isInteger(value.bidder_id))return null;
    return value as AuctionStreamPayload;
  }catch{
    return null;
  }
}

function selectedAuctionIds(){
  return [...new Set([...subscribers].flatMap(subscriber=>[...subscriber.auctionIds]))].sort((left,right)=>left-right);
}

function closeSharedEventSource(){
  if(!sharedEventSource)return;
  sharedListeners.forEach(([type,listener])=>sharedEventSource?.removeEventListener(type,listener));
  sharedEventSource.close();
  sharedEventSource=null;
  sharedListeners=[];
  sharedSubscriptionSignature='';
}

function syncSharedEventSource(){
  const auctionIds=selectedAuctionIds();
  const signature=auctionIds.join(',');
  if(!signature){
    closeSharedEventSource();
    hasOpenedSharedEventSource=false;
    return;
  }
  if(sharedEventSource&&sharedSubscriptionSignature===signature)return;
  closeSharedEventSource();

  const eventSource=new EventSource(auctionStreamUrl(auctionIds));
  sharedEventSource=eventSource;
  sharedSubscriptionSignature=signature;
  eventSource.onopen=()=>{
    if(hasOpenedSharedEventSource)subscribers.forEach(subscriber=>subscriber.onReconnected?.());
    hasOpenedSharedEventSource=true;
  };
  sharedListeners=AUCTION_STREAM_EVENT_TYPES.map(type=>{
    const listener:EventListener=event=>{
      const payload=parsePayload(type,event as MessageEvent<string>);
      if(payload)subscribers.forEach(subscriber=>{
        if(subscriber.auctionIds.has(payload.auction_id))subscriber.onAuctionUpdated(payload);
      });
    };
    eventSource.addEventListener(type,listener);
    return [type,listener] as const;
  });
}

function subscribeToAuctionStream(subscriber:AuctionStreamSubscriber){
  subscribers.add(subscriber);
  syncSharedEventSource();

  return ()=>{
    subscribers.delete(subscriber);
    syncSharedEventSource();
  };
}

export function useAuctionStream({
  auctionIds,
  enabled=true,
  onAuctionUpdated,
  onReconnected,
}:UseAuctionStreamOptions){
  const onAuctionUpdatedRef=useRef(onAuctionUpdated);
  const onReconnectedRef=useRef(onReconnected);
  onAuctionUpdatedRef.current=onAuctionUpdated;
  onReconnectedRef.current=onReconnected;

  const auctionIdSignature=[...new Set(auctionIds.filter(auctionId=>Number.isInteger(auctionId)&&auctionId>0))]
    .sort((left,right)=>left-right).join(',');

  useEffect(()=>{
    if(!enabled||isMockApiEnabled()||!auctionIdSignature)return;
    return subscribeToAuctionStream({
      auctionIds:new Set(auctionIdSignature.split(',').map(Number)),
      onAuctionUpdated:payload=>onAuctionUpdatedRef.current(payload),
      onReconnected:()=>onReconnectedRef.current?.(),
    });
  },[auctionIdSignature,enabled]);
}
