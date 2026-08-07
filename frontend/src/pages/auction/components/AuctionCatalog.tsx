import {CheckCircle2,Clock3,Search} from 'lucide-react';
import {useEffect,useRef,useState} from 'react';
import {Link} from 'react-router-dom';
import {AuctionBidDialog} from '../../../components';
import type {AuctionDto} from '../../../dto/auctionDto';
import CardArtwork from '../../cards/components/CardArtwork';
import {useAuthGate} from '../../../auth/useAuthGate';
import {useCurrentUserId} from '../../../auth/useCurrentUserId';
import {normalizePsaGrade} from '../../../api/auctionMapper';
import {nowUtc,parseUtc} from '../../../utils/utc';

const remainingTime=(endsAt:string,now:number)=>{
  const total=Math.max(0,Math.ceil((parseUtc(endsAt)-now)/1000));
  if(total===0)return '경매 종료';
  const hours=Math.floor(total/3600);
  const minutes=Math.floor(total%3600/60);
  const seconds=total%60;
  return `${String(hours).padStart(2,'0')}:${String(minutes).padStart(2,'0')}:${String(seconds).padStart(2,'0')}`;
};

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

export default function AuctionCatalog({auctions}:{auctions:AuctionDto[]}){
  const authGate=useAuthGate();
  const currentUserId=useCurrentUserId();
  const[selectedAuction,setSelectedAuction]=useState<AuctionDto|null>(null);
  const[now,setNow]=useState(nowUtc());
  useEffect(()=>{
    const timer=window.setInterval(()=>setNow(nowUtc()),1000);
    return()=>window.clearInterval(timer);
  },[]);
  if(!auctions.length)return <div className="filter-empty"><Search/><b>조건에 맞는 경매가 없습니다.</b><span>검색어나 필터를 변경해 보세요.</span></div>;
  return <><section className="card-grid">{auctions.map(auction=>{const remaining=remainingTime(auction.endsAt,now),ended=!['OPEN','ENDING'].includes(auction.status)||remaining==='경매 종료',isSeller=currentUserId!==null&&auction.sellerId===currentUserId,buttonState=auction.myBidStatus==='LEADING'?'leading':auction.myBidStatus==='OUTBID'?'outbid':'new',increase=auction.currentPrice-auction.startPrice,increaseRate=auction.startPrice>0?increase/auction.startPrice*100:0,psaGrade=normalizePsaGrade(auction.card.psaGrade);return <article className={`card-tile up${ended?' ended':''}`} key={auction.id}>
    <div className="auction-image-viewport"><CardArtwork theme={auction.card.theme} imageUrl={auction.card.imageUrl} name={auction.card.name}/></div>
    <div>
      <div className="card-meta"><span><span className="grade">PSA {psaGrade}</span><span className="grade">{auction.card.language}</span></span><span className="auction-countdown"><Clock3/>{remaining}{!ended&&' 남음'}</span></div>
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
