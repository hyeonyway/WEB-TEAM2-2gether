import {useEffect,useRef} from 'react';
import {isMockApiEnabled} from '../api/mockApiConfig';
import type {AuctionUpdatedEventDto} from '../dto/auctionEventDto';
import {parseAuctionUpdatedEvent} from '../dto/auctionEventDto';

const EVENT_BATCH_WINDOW_MS=200;

type UseAuctionStreamOptions={
  enabled?:boolean;
  onAuctionUpdated:(events:AuctionUpdatedEventDto[])=>void;
};

function auctionStreamUrl(){
  const apiBaseUrl=(import.meta.env.VITE_API_BASE_URL??'').replace(/\/+$/,'');
  return `${apiBaseUrl}/api/auctions/stream`;
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
    let updateTimer:ReturnType<typeof setTimeout>|undefined;
    const pendingEvents=new Map<number,AuctionUpdatedEventDto>();

    const scheduleUpdate=(message:MessageEvent<string>)=>{
      const event=parseAuctionUpdatedEvent(message.data);
      if(!event)return;
      const pending=pendingEvents.get(event.auction_id);
      if(!pending
        ||event.auction_version>pending.auction_version
        ||(event.auction_version===pending.auction_version
          &&event.occurred_at>pending.occurred_at)){
        pendingEvents.set(event.auction_id,event);
      }
      if(updateTimer!==undefined)clearTimeout(updateTimer);
      updateTimer=setTimeout(()=>{
        updateTimer=undefined;
        const eventsToDispatch=[...pendingEvents.values()].sort((a,b)=>
          a.occurred_at.localeCompare(b.occurred_at),
        );
        pendingEvents.clear();
        if(eventsToDispatch.length)onAuctionUpdatedRef.current(eventsToDispatch);
      },EVENT_BATCH_WINDOW_MS);
    };

    eventSource.addEventListener('auction-updated',scheduleUpdate);

    return ()=>{
      if(updateTimer!==undefined)clearTimeout(updateTimer);
      eventSource.removeEventListener('auction-updated',scheduleUpdate);
      eventSource.close();
    };
  },[enabled]);
}
