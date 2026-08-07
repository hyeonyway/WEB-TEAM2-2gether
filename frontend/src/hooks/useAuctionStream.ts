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
  enabled?:boolean;
  onAuctionUpdated:(payload:AuctionStreamPayload)=>void;
  onReplayReset?:()=>void;
};

type AuctionStreamSubscriber={
  onAuctionUpdated:(payload:AuctionStreamPayload)=>void;
  onReplayReset?:()=>void;
};

const subscribers=new Set<AuctionStreamSubscriber>();
let sharedEventSource:EventSource|null=null;
let sharedListeners:ReadonlyArray<readonly[string,EventListener]>=[];
const REPLAY_RESET_EVENT='replay-reset';

function auctionStreamUrl(){
  const apiBaseUrl=(import.meta.env.VITE_API_BASE_URL??'').replace(/\/+$/,'');
  return `${apiBaseUrl}/api/auctions/stream`;
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

function connectSharedEventSource(){
  if(sharedEventSource)return;

  const eventSource=new EventSource(auctionStreamUrl());
  sharedEventSource=eventSource;
  sharedListeners=AUCTION_STREAM_EVENT_TYPES.map(type=>{
    const listener:EventListener=event=>{
      const payload=parsePayload(type,event as MessageEvent<string>);
      if(payload)subscribers.forEach(subscriber=>subscriber.onAuctionUpdated(payload));
    };
    eventSource.addEventListener(type,listener);
    return [type,listener] as const;
  });
  const replayResetListener:EventListener=()=>{
    subscribers.forEach(subscriber=>subscriber.onReplayReset?.());
  };
  eventSource.addEventListener(REPLAY_RESET_EVENT,replayResetListener);
  sharedListeners=[...sharedListeners,[REPLAY_RESET_EVENT,replayResetListener]];
}

function subscribeToAuctionStream(subscriber:AuctionStreamSubscriber){
  subscribers.add(subscriber);
  connectSharedEventSource();

  return ()=>{
    subscribers.delete(subscriber);
    if(subscribers.size>0||!sharedEventSource)return;

    sharedListeners.forEach(([type,listener])=>sharedEventSource?.removeEventListener(type,listener));
    sharedEventSource.close();
    sharedEventSource=null;
    sharedListeners=[];
  };
}

export function useAuctionStream({
  enabled=true,
  onAuctionUpdated,
  onReplayReset,
}:UseAuctionStreamOptions){
  const onAuctionUpdatedRef=useRef(onAuctionUpdated);
  const onReplayResetRef=useRef(onReplayReset);
  onAuctionUpdatedRef.current=onAuctionUpdated;
  onReplayResetRef.current=onReplayReset;

  useEffect(()=>{
    if(!enabled||isMockApiEnabled())return;
    return subscribeToAuctionStream({
      onAuctionUpdated:payload=>onAuctionUpdatedRef.current(payload),
      onReplayReset:()=>onReplayResetRef.current?.(),
    });
  },[enabled]);
}
