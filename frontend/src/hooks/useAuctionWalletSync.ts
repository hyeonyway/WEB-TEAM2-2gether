import {useQueryClient} from '@tanstack/react-query';
import {walletQueryKeys} from '../queries/walletQueryKeys';
import type {AuctionStreamPayload} from './useAuctionStream';
import {useAuctionStream} from './useAuctionStream';


function affectsWallet(event:AuctionStreamPayload,userId:number):boolean{
  if(event.type==='BID_PLACED'){
    return event.bidder_id===userId||event.previous_bidder_id===userId;
  }
  return event.type==='AUCTION_CLOSED'&&event.winner_id===userId;
}

export function useAuctionWalletSync(userId:number|null,authenticated:boolean){
	const queryClient=useQueryClient();

  useAuctionStream({
    enabled:authenticated&&userId!==null,
    onAuctionUpdated:event=>{
      if(userId===null||!affectsWallet(event,userId))return;
      void queryClient.invalidateQueries({queryKey:walletQueryKeys.balance()});
    },
    onReplayReset:()=>{
      void queryClient.invalidateQueries({queryKey:walletQueryKeys.balance()});
    },
    onReconnected:()=>{
      void queryClient.invalidateQueries({queryKey:walletQueryKeys.balance()});
    },
  });
}
