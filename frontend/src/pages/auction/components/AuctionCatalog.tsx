import {CheckCircle2,Clock3,Search} from 'lucide-react';
import {useEffect,useState} from 'react';
import {AuctionBidDialog} from '../../../components';
import type {AuctionDto} from '../../../dto/auctionDto';
import CardArtwork from '../../cards/components/CardArtwork';
import FavoriteButton from './FavoriteButton';

const remainingTime=(endsAt:string,now:number)=>{
  const total=Math.max(0,Math.ceil((new Date(endsAt).getTime()-now)/1000));
  if(total===0)return '경매 종료';
  const hours=Math.floor(total/3600);
  const minutes=Math.floor(total%3600/60);
  const seconds=total%60;
  return `${String(hours).padStart(2,'0')}:${String(minutes).padStart(2,'0')}:${String(seconds).padStart(2,'0')}`;
};

export default function AuctionCatalog({auctions}:{auctions:AuctionDto[]}){
  const[selectedAuction,setSelectedAuction]=useState<AuctionDto|null>(null);
  const[now,setNow]=useState(Date.now());
  useEffect(()=>{
    const timer=window.setInterval(()=>setNow(Date.now()),1000);
    return()=>window.clearInterval(timer);
  },[]);
  if(!auctions.length)return <div className="filter-empty"><Search/><b>조건에 맞는 경매가 없습니다.</b><span>검색어나 필터를 변경해 보세요.</span></div>;
  return <><section className="card-grid">{auctions.map(auction=>{const remaining=remainingTime(auction.endsAt,now),ended=auction.status==='ENDED'||remaining==='경매 종료',buttonState=auction.myBidStatus==='LEADING'?'leading':auction.myBidStatus==='OUTBID'?'outbid':'new',increase=auction.currentPrice-auction.startPrice,increaseRate=auction.startPrice>0?increase/auction.startPrice*100:0;return <article className={`card-tile up${ended?' ended':''}`} key={auction.id}>
    <FavoriteButton auctionId={auction.id}/>
    <CardArtwork theme={auction.card.theme}/>
    <div>
      <div className="card-meta"><span><span className="grade">PSA {auction.card.psaGrade}</span><span className="grade">{auction.card.language}</span></span><span className="auction-countdown"><Clock3/>{remaining}{!ended&&' 남음'}</span></div>
      <h3>{auction.card.name}</h3><small>현재 경매가</small>
      <div className="tile-price"><strong>{auction.currentPrice.toLocaleString()}원</strong><em>시작가 대비 +{increaseRate.toFixed(1)}%</em></div>
      <div className="auction-card-info">
        <span>시작가<b>{auction.startPrice.toLocaleString()}원</b></span>
        <span>누적 상승액<b className="increase-value">+{increase.toLocaleString()}원</b></span>
        <span>다음 최소 입찰가<b>{(auction.currentPrice+auction.bidIncrement).toLocaleString()}원</b></span>
        <span>총 입찰<b>{auction.bidCount.toLocaleString()}회</b></span>
      </div>
      <div className="card-actions">
        <button className="card-detail-button" type="button" onClick={()=>window.location.href=`/auction/${auction.id}`}>상세보기</button>
        <button className={`card-bid-button ${buttonState}`} type="button" disabled={ended} onClick={()=>setSelectedAuction(auction)}>
          {ended?'경매 종료':auction.myBidStatus==='LEADING'?<><CheckCircle2/><span><b>내가 최고가 입찰 중</b><small>현재 1위 · 입찰 현황 보기</small></span></>:auction.myBidStatus==='OUTBID'?<><b>상회 입찰 필요</b><small>내 입찰 {(auction.myBidAmount??0).toLocaleString()}원</small></>:<b>입찰하기</b>}
        </button>
      </div>
    </div>
  </article>})}</section>{selectedAuction&&<AuctionBidDialog auction={selectedAuction} onClose={()=>setSelectedAuction(null)}/>}</>;
}
