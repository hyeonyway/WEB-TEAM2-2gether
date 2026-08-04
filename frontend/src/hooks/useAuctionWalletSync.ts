import {useQueryClient} from '@tanstack/react-query';
import {walletQueryKeys} from '../queries/walletQueryKeys';
import type {AuctionStreamPayload} from './useAuctionStream';
import {useAuctionStream} from './useAuctionStream';

function decodeUserId(accessToken:string|null):number|null{
  if(!accessToken)return null;
  try{
    const payload=accessToken.split('.')[1];
    if(!payload)return null;
    const normalized=payload.replaceAll('-','+').replaceAll('_','/');
    const padded=normalized.padEnd(Math.ceil(normalized.length/4)*4,'=');
    const subject=(JSON.parse(atob(padded)) as {sub?:unknown}).sub;
    const userId=Number(subject);
    return Number.isInteger(userId)&&userId>0?userId:null;
  }catch{
    return null;
  }
}

function affectsWallet(event:AuctionStreamPayload,userId:number):boolean{
  if(event.type==='BID_PLACED'){
    return event.bidder_id===userId||event.previous_bidder_id===userId;
  }
  return event.type==='AUCTION_CLOSED'&&event.winner_id===userId;
}

export function useAuctionWalletSync(accessToken:string|null,authenticated:boolean){
  const queryClient=useQueryClient();
  const userId=decodeUserId(accessToken);

  useAuctionStream({
    enabled:authenticated&&userId!==null,
    onAuctionUpdated:event=>{
      if(userId===null||!affectsWallet(event,userId))return;
      void queryClient.invalidateQueries({queryKey:walletQueryKeys.balance()});
    },
  });
}
