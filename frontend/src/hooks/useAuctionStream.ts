import {useEffect,useRef} from 'react';
import {isMockApiEnabled} from '../api/mockApiConfig';

const INVALIDATION_DEBOUNCE_MS=200;

type UseAuctionStreamOptions={
  enabled?:boolean;
  onAuctionUpdated:()=>void;
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

    const scheduleUpdate=()=>{
      if(updateTimer!==undefined)clearTimeout(updateTimer);
      updateTimer=setTimeout(()=>{
        updateTimer=undefined;
        onAuctionUpdatedRef.current();
      },INVALIDATION_DEBOUNCE_MS);
    };

    eventSource.addEventListener('open',scheduleUpdate);
    eventSource.addEventListener('auction-updated',scheduleUpdate);

    return ()=>{
      if(updateTimer!==undefined)clearTimeout(updateTimer);
      eventSource.removeEventListener('open',scheduleUpdate);
      eventSource.removeEventListener('auction-updated',scheduleUpdate);
      eventSource.close();
    };
  },[enabled]);
}
