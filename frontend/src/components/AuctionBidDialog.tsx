import {CheckCircle2,Wallet} from 'lucide-react';
import {useEffect,useRef,useState} from 'react';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import type {AuctionDto,BidContextResponseDto} from '../dto/auctionDto';
import {createAuctionBid} from '../api/auctionApi';
import {applyBidContextEvent,auctionQueries,auctionQueryKeys} from '../queries/auctionQueries';
import {useAuctionStream} from '../hooks/useAuctionStream';
import {walletQueryKeys} from '../queries/walletQueryKeys';

function AnimatedBidValue({value}:{value:number}){
  const[displayValue,setDisplayValue]=useState(value);
  const displayValueRef=useRef(value);

  useEffect(()=>{
    if(displayValueRef.current===value)return;
    if(window.matchMedia('(prefers-reduced-motion: reduce)').matches){
      displayValueRef.current=value;
      setDisplayValue(value);
      return;
    }
    const start=displayValueRef.current;
    const startedAt=performance.now();
    let animationFrame=0;
    const animate=(now:number)=>{
      const progress=Math.min((now-startedAt)/650,1);
      const next=Math.round(start+(value-start)*(1-Math.pow(1-progress,3)));
      displayValueRef.current=next;
      setDisplayValue(next);
      if(progress<1)animationFrame=requestAnimationFrame(animate);
    };
    animationFrame=requestAnimationFrame(animate);
    return()=>cancelAnimationFrame(animationFrame);
  },[value]);

  return <>{displayValue.toLocaleString()}원</>;
}
export default function AuctionBidDialog({auction,onClose}:{auction:AuctionDto;onClose:()=>void}){
  const queryClient=useQueryClient();
  const contextQuery=useQuery(auctionQueries.bidContext(auction.id));
  const context=contextQuery.data;
  useAuctionStream({
    onAuctionUpdated:event=>{
      if(event.auction_id!==auction.id)return;
      const bidContextKey=auctionQueryKeys.bidContext(auction.id);
      queryClient.setQueryData<BidContextResponseDto>(bidContextKey,current=>applyBidContextEvent(current,event));
    },
    onReplayReset:()=>{
      void Promise.all([
        queryClient.invalidateQueries({queryKey:auctionQueryKeys.bidContext(auction.id)}),
        queryClient.invalidateQueries({queryKey:auctionQueryKeys.bids(auction.id)}),
      ]);
    },
    onReconnected:()=>{
      void Promise.all([
        queryClient.invalidateQueries({queryKey:auctionQueryKeys.bidContext(auction.id)}),
        queryClient.invalidateQueries({queryKey:auctionQueryKeys.bids(auction.id)}),
      ]);
    },
  });
  const wallet=context?.wallet.available_balance??0;
  const currentPrice=context?.current_price??auction.currentPrice;
  const bidIncrement=context?.bid_increment??auction.bidIncrement;
  const minimum=context?.minimum_bid??currentPrice+bidIncrement;
  const[amount,setAmount]=useState<number|string>(minimum);
  useEffect(()=>{
    setAmount(current=>{
      const currentValue=Number(current);
      return current===''||!Number.isFinite(currentValue)||currentValue<minimum
        ?minimum
        :current;
    });
  },[minimum]);
  const amountValue=Number(amount);
  const belowMinimum=amount===''||amountValue<minimum;
  const insufficient=amountValue>wallet;
  const leading=(context?.my_bid_status??auction.myBidStatus)==='LEADING';
  const closed=!['OPEN','ENDING'].includes(context?.status??auction.status);
  const bidMutation=useMutation({
    mutationFn:()=>createAuctionBid(auction.id,amountValue,crypto.randomUUID()),
    onSuccess:async()=>{
      await Promise.all([
        queryClient.invalidateQueries({queryKey:auctionQueryKeys.all}),
        queryClient.invalidateQueries({queryKey:auctionQueryKeys.bidContext(auction.id)}),
        queryClient.invalidateQueries({queryKey:walletQueryKeys.balance()}),
      ]);
      onClose();
    },
  });

  return <div className="bid-backdrop" onMouseDown={event=>event.target===event.currentTarget&&onClose()}>
    <section className="bid-dialog" role="dialog" aria-modal="true" aria-label={`${auction.card.name} 입찰`}>
      <button className="bid-close" onClick={onClose} aria-label="닫기">×</button>
      <small>실시간 카드 경매</small><h2>입찰하기</h2><p className="bid-card-name">{auction.card.name}</p>
      {leading&&<div className="bid-leading-notice"><CheckCircle2/><span><b>현재 최고가 입찰 중입니다.</b><small>입찰 현황은 확인할 수 있지만 추가 입찰은 제한됩니다.</small></span></div>}
      <div className="bid-wallet"><span><Wallet/>전자지갑 포인트</span><strong>{wallet.toLocaleString()}P</strong></div>
      <div className="bid-current"><span>현재 경매가<b><AnimatedBidValue value={currentPrice}/></b></span><span>최소 입찰가<b><AnimatedBidValue value={minimum}/></b></span></div>
      <h3>입찰가 선택</h3>
      <div className="bid-options">{[minimum,minimum+bidIncrement*4,minimum+bidIncrement*9].map(value=><button key={value} className={amountValue===value?'active':''} disabled={closed||leading||bidMutation.isPending} onClick={()=>setAmount(value)}>{value.toLocaleString()}원</button>)}</div>
      <label>직접 입력<div className={belowMinimum?'invalid':''}><input disabled={closed||leading||bidMutation.isPending} type="number" step={bidIncrement} value={amount} onChange={event=>setAmount(event.target.value)}/><span>원</span></div>{belowMinimum&&<small className="bid-minimum-error">최소 입찰가 {minimum.toLocaleString()}원 이상 입력해 주세요.</small>}</label>
      <section className="bid-history"><div className="bid-history-head"><h3>경매 입찰 내역</h3><span>최근 {context?.recent_bids.length??0}건</span></div><div className="bid-history-list">{contextQuery.isPending?<p>불러오는 중...</p>:context?.recent_bids.map(bid=><div className={`bid-history-row${bid.id<0?' bid-history-row-live':''}`} key={bid.id}><span><b>{bid.bidder_alias}</b><small>{bid.is_highest?'최고 입찰':'입찰 완료'}</small></span><strong>{bid.amount.toLocaleString()}원</strong><time>{new Date(bid.created_at).toLocaleString('ko-KR')}</time></div>)}</div></section>
      <div className="bid-balance"><span>입찰 후 잔여 포인트</span><b>{Math.max(0,wallet-(amountValue||0)).toLocaleString()}P</b></div>
      {insufficient&&<p className="bid-error">전자지갑 포인트가 부족합니다.</p>}
      {bidMutation.isError&&<p className="bid-error">입찰하지 못했습니다. 현재 가격과 잔액을 다시 확인해 주세요.</p>}
      <button className="bid-submit" disabled={contextQuery.isPending||belowMinimum||insufficient||closed||leading||bidMutation.isPending} onClick={()=>bidMutation.mutate()}>{closed?'경매 종료':leading?'현재 최고가 입찰 중':bidMutation.isPending?'입찰 처리 중...':`${amountValue.toLocaleString()}원 입찰하기`}</button>
    </section>
  </div>;
}
