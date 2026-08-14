import {CheckCircle2,Clock3,Search} from 'lucide-react';
import {useEffect,useRef,useState} from 'react';
import {Link} from 'react-router-dom';
import {AuctionBidDialog} from '../../../components';
import type {AuctionDto} from '../../../dto/auctionDto';
import CardArtwork from '../../cards/components/CardArtwork';
import {useAuthGate} from '../../../auth/useAuthGate';
import {useCurrentUserId} from '../../../auth/useCurrentUserId';
import {normalizePsaGrade} from '../../../api/auctionMapper';
import {displayRemaining,formatRemaining,isAuctionEnded,useCountdownNow} from '../../../hooks/useCountdown';

function AnimatedAuctionPrice({price}:{price:number}){
  const previousPrice=useRef(price);
  const priceElement=useRef<HTMLSpanElement>(null);
  const[pulse,setPulse]=useState(0);

  useEffect(()=>{
    if(price>previousPrice.current){
      setPulse(value=>value+1);
      if(!window.matchMedia('(prefers-reduced-motion: reduce)').matches){
        priceElement.current?.closest<HTMLElement>('.card-tile')?.animate([
          {backgroundColor:'#ffffff',boxShadow:'0 2px 3px #00000008'},
          {backgroundColor:'#fff0c9',boxShadow:'0 8px 28px #f0a42935',offset:.35},
          {backgroundColor:'#ffe0db',boxShadow:'0 8px 30px #f0524935',offset:.62},
          {backgroundColor:'#ffffff',boxShadow:'0 2px 3px #00000008'},
        ],{
          duration:950,
          easing:'cubic-bezier(.2,.8,.2,1)',
        });
      }
    }
    previousPrice.current=price;
  },[price]);

  return <strong aria-live="polite">
    <span ref={priceElement} key={pulse} className={pulse>0?'auction-price-rise':undefined}>
      {price.toLocaleString()}원
    </span>
  </strong>;
}

type AuctionCatalogProps={
  auctions:AuctionDto[];
  onSubscriptionAuctionIdsChange?:(auctionIds:readonly number[])=>void;
};

// 서버 SSE 구독 캡(16)에서 입찰 모달의 독립 구독 1개 몫을 뺀 값. 화면 축소 등으로 실제
// 보이는 카드가 이보다 많아지면 뒤(§updateSubscription)에서 우선순위를 매겨 잘라낸다.
const MAX_SUBSCRIBED_AUCTIONS=15;

export default function AuctionCatalog({auctions,onSubscriptionAuctionIdsChange}:AuctionCatalogProps){
  const authGate=useAuthGate();
  const currentUserId=useCurrentUserId();
  const[selectedAuction,setSelectedAuction]=useState<AuctionDto|null>(null);
  const now=useCountdownNow();
  const catalogRef=useRef<HTMLElement>(null);
  const subscribedAuctionIdsRef=useRef<ReadonlySet<number>>(new Set());
  const auctionIdSignature=auctions.map(auction=>auction.id).join(',');

  useEffect(()=>{
    if(!onSubscriptionAuctionIdsChange)return;
    const auctionIds=auctionIdSignature===''?[]:auctionIdSignature.split(',').map(Number);
    if(!auctionIds.length){
      subscribedAuctionIdsRef.current=new Set();
      onSubscriptionAuctionIdsChange([]);
      return;
    }
    const previousIds=subscribedAuctionIdsRef.current;
    const currentAuctionIdSet=new Set(auctionIds);
    if([...previousIds].some(auctionId=>!currentAuctionIdSet.has(auctionId))){
      subscribedAuctionIdsRef.current=new Set();
    }
    const updateSubscription=(visibleAuctionIds:ReadonlySet<number>)=>{
      const visibleIndexes=auctionIds.flatMap((auctionId,index)=>visibleAuctionIds.has(auctionId)?[index]:[]);
      if(!visibleIndexes.length)return;
      if([...visibleAuctionIds].every(auctionId=>subscribedAuctionIdsRef.current.has(auctionId)))return;
      const first=Math.min(...visibleIndexes);
      const last=Math.max(...visibleIndexes);
      const bufferedIndexes=[];
      for(let index=Math.max(0,first-3);index<Math.min(auctionIds.length,last+4);index++)bufferedIndexes.push(index);
      // 화면에 진짜 보이는 카드를 최우선으로 채우고, 남는 자리에 스크롤 예측용 버퍼를 채운다.
      // 화면 축소 등으로 실제 보이는 카드 수가 서버 캡을 넘으면 버퍼부터 잘리고, 그래도 넘치면
      // 보이는 카드 자체도 잘린다 — 일부 카드가 잠시 실시간 갱신을 못 받을 수는 있어도,
      // 캡을 넘겨서 SSE 구독 자체가 통째로 끊기는 것보다는 훨씬 낫다.
      const visibleIndexSet=new Set(visibleIndexes);
      const prioritizedIndexes=[...visibleIndexes,...bufferedIndexes.filter(index=>!visibleIndexSet.has(index))]
        .slice(0,MAX_SUBSCRIBED_AUCTIONS);
      const nextAuctionIds=[...new Set(prioritizedIndexes)].sort((left,right)=>left-right)
        .map(index=>auctionIds[index]);
      subscribedAuctionIdsRef.current=new Set(nextAuctionIds);
      onSubscriptionAuctionIdsChange(nextAuctionIds);
    };
    if(typeof IntersectionObserver==='undefined'){
      const nextAuctionIds=auctionIds.slice(0,MAX_SUBSCRIBED_AUCTIONS);
      subscribedAuctionIdsRef.current=new Set(nextAuctionIds);
      onSubscriptionAuctionIdsChange(nextAuctionIds);
      return;
    }
    const visibleAuctionIds=new Set<number>();
    const observer=new IntersectionObserver(entries=>{
      entries.forEach(entry=>{
        const auctionId=Number((entry.target as HTMLElement).dataset.auctionId);
        if(!Number.isInteger(auctionId))return;
        if(entry.isIntersecting)visibleAuctionIds.add(auctionId);
        else visibleAuctionIds.delete(auctionId);
      });
      updateSubscription(visibleAuctionIds);
    });
    catalogRef.current?.querySelectorAll<HTMLElement>('[data-auction-id]').forEach(card=>observer.observe(card));
    return()=>observer.disconnect();
  },[auctionIdSignature,onSubscriptionAuctionIdsChange]);

  if(!auctions.length)return <div className="filter-empty"><Search/><b>조건에 맞는 경매가 없습니다.</b><span>검색어나 필터를 변경해 보세요.</span></div>;
  return <><section className="card-grid" ref={catalogRef}>{auctions.map(auction=>{const remaining=formatRemaining(auction.endsAt,now),displayedRemaining=displayRemaining(auction.status,auction.endsAt,now),ended=isAuctionEnded(auction.status,remaining),isSeller=currentUserId!==null&&auction.sellerId===currentUserId,buttonState=auction.myBidStatus==='LEADING'?'leading':auction.myBidStatus==='OUTBID'?'outbid':'new',increase=auction.currentPrice-auction.startPrice,increaseRate=auction.startPrice>0?increase/auction.startPrice*100:0,psaGrade=normalizePsaGrade(auction.card.psaGrade);return <article className={`card-tile up${ended?' ended':''}`} data-auction-id={auction.id} key={auction.id}>
    <div className="auction-image-viewport"><CardArtwork theme={auction.card.theme} imageUrl={auction.card.imageUrl} name={auction.card.name}/></div>
    <div>
      <div className="card-meta"><span><span className="grade">PSA {psaGrade}</span><span className="grade">{auction.card.language}</span></span><span className={`auction-countdown${auction.status==='ENDING'?' ending':''}`}><Clock3/>{displayedRemaining}{!ended&&auction.status!=='ENDING'&&' 남음'}</span></div>
      <h3>{auction.card.name}</h3><small>현재 경매가</small>
      <div className="tile-price"><AnimatedAuctionPrice price={auction.currentPrice}/><em>시작가 대비 +{increaseRate.toFixed(1)}%</em></div>
      <div className="auction-card-info">
        <span>시작가<b>{auction.startPrice.toLocaleString()}원</b></span>
        <span>누적 상승액<b className="increase-value">+{increase.toLocaleString()}원</b></span>
        <span>다음 최소 입찰가<b>{(auction.currentPrice+auction.bidIncrement).toLocaleString()}원</b></span>
        <span>총 입찰<b>{auction.bidCount.toLocaleString()}회</b></span>
      </div>
      <div className="card-actions">
        <Link className="card-detail-button" to={`/auction/${auction.id}`}>상세보기</Link>
        <button className={`card-bid-button ${buttonState}`} type="button" disabled={ended||isSeller} onClick={()=>{if(authGate.requestNavigation())setSelectedAuction(auction)}}>
          {ended?'경매 종료':isSeller?<b>내가 등록한 경매</b>:auction.myBidStatus==='LEADING'?<><CheckCircle2/><span><b>내가 최고가 입찰 중</b><small>현재 1위 · 입찰 현황 보기</small></span></>:auction.myBidStatus==='OUTBID'?<><b>상회 입찰 필요</b><small>내 입찰 {(auction.myBidAmount??0).toLocaleString()}원</small></>:<b>입찰하기</b>}
        </button>
      </div>
    </div>
  </article>})}</section>{selectedAuction&&<AuctionBidDialog auction={selectedAuction} onClose={()=>setSelectedAuction(null)}/>}</>;
}
