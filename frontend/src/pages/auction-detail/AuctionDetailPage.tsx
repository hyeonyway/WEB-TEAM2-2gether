import {useEffect,useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {ChevronRight,Clock3,Info,Wallet} from 'lucide-react';
import {useParams} from 'react-router-dom';
import {AuctionBidDialog,Header} from '../../components';
import {mapAuction,mapCardLanguage,normalizePsaGrade} from '../../api/auctionMapper';
import {auctionQueries} from '../../queries/auctionQueries';
import {useAuthGate} from '../../auth/useAuthGate';
import AuctionDetailSkeleton from './AuctionDetailSkeleton';
import AuctionImageGallery from './AuctionImageGallery';

function useAuctionNow(){
  const[now,setNow]=useState(Date.now());
  useEffect(()=>{
    const timer=window.setInterval(()=>setNow(Date.now()),1000);
    return()=>window.clearInterval(timer);
  },[]);
  return now;
}

function formatRemaining(endsAt:string,now:number){
  const total=Math.max(0,Math.ceil((new Date(endsAt).getTime()-now)/1000));
  if(total===0)return '경매 종료';
  const hours=Math.floor(total/3600);
  const minutes=Math.floor(total%3600/60);
  const seconds=total%60;
  return `${String(hours).padStart(2,'0')}:${String(minutes).padStart(2,'0')}:${String(seconds).padStart(2,'0')}`;
}

function formatBidTime(createdAt:string){
  return new Intl.DateTimeFormat('ko-KR',{
    month:'short',
    day:'numeric',
    hour:'2-digit',
    minute:'2-digit',
  }).format(new Date(createdAt));
}

export default function AuctionDetailPage(){
  const params=useParams();
  const auctionId=Number(params.auctionId);
  const validAuctionId=Number.isInteger(auctionId)&&auctionId>0;
  const now=useAuctionNow();
  const[bidOpen,setBidOpen]=useState(false);
  const authGate=useAuthGate();
  const authenticated=authGate.status==='authenticated';
  const viewerScope=authenticated?'self':'public';
  const detailQuery=useQuery({
    ...auctionQueries.detail(auctionId,viewerScope),
    enabled:validAuctionId,
  });
  const contextQuery=useQuery({
    ...auctionQueries.bidContext(auctionId),
    enabled:validAuctionId&&authenticated,
  });
  const bidsQuery=useQuery({
    ...auctionQueries.bids(auctionId),
    enabled:validAuctionId,
  });

  if(!validAuctionId){
    return <div className="detail-page auction-detail-page"><Header/><main className="auction-detail-shell"><p className="form-error">잘못된 경매 번호입니다.</p></main></div>;
  }
  if(detailQuery.isPending||bidsQuery.isPending||(authenticated&&contextQuery.isPending)){
    return <div className="detail-page auction-detail-page"><Header/><AuctionDetailSkeleton/></div>;
  }
  if(detailQuery.error||bidsQuery.error||!detailQuery.data||!bidsQuery.data){
    return <div className="detail-page auction-detail-page"><Header/><main className="auction-detail-shell"><p className="form-error">경매 정보를 불러오지 못했습니다.</p></main></div>;
  }

  const detail=detailQuery.data;
  const context=authenticated?contextQuery.data:undefined;
  const currentPrice=context?.current_price??detail.current_price;
  const minimumBid=context?.minimum_bid??detail.minimum_bid;
  const recentBids=bidsQuery.data.content;
  const auction=mapAuction(detail);
  const grade=normalizePsaGrade(detail.card.psa_grade);
  const language=mapCardLanguage(detail.card.language);
  const remaining=formatRemaining(detail.ends_at,now);
  const ended=!['OPEN','ENDING'].includes(detail.status)||remaining==='경매 종료';
  const increaseRate=detail.start_price>0
    ?(currentPrice-detail.start_price)/detail.start_price*100
    :0;

  return <div className="detail-page auction-detail-page"><Header/><main className="auction-detail-shell">
    <div className="auction-detail-layout">
      <section className="auction-detail-product">
        <AuctionImageGallery cardName={detail.card.name} cardImage={detail.card.thumbnail_url} photos={detail.photos}/>
      </section>
      <section className="auction-bid-panel">
        <div className="auction-detail-title">
          <div className="detail-grades"><span className="grade">PSA {grade}</span><span className="grade">{language}</span></div>
          <h1>{detail.card.name}</h1>
          <p>{detail.card.set_name} · {detail.card.language}</p>
          <small>경매번호 AUCTION-{String(detail.id).padStart(4,'0')}</small>
        </div>
        <div className="auction-live-label"><i/> {ended?'종료된 경매':'LIVE 경매'} <span><Clock3/>{remaining}</span></div>
        <div className="auction-current-price"><small>현재 입찰가</small><strong>{currentPrice.toLocaleString()}원</strong><em>+{increaseRate.toFixed(1)}%</em></div>
        <div className="auction-bid-summary">
          <span>다음 최소 입찰가<b>{minimumBid.toLocaleString()}원</b></span>
          <span>누적 입찰 수<b>{detail.bid_count.toLocaleString()}건</b></span>
          <span>PSA 등급<b>PSA {grade}</b></span>
        </div>
        {context&&<div className="auction-wallet-summary"><span><Wallet/>보유 포인트</span><strong>{context.wallet.available_balance.toLocaleString()}P</strong></div>}
        <button className="auction-detail-bid-button" disabled={ended} onClick={()=>{if(authGate.requestNavigation())setBidOpen(true)}}>
          {ended?'경매 종료':`${minimumBid.toLocaleString()}원부터 입찰하기`}
        </button>
        <a className="auction-card-price-link" href={`/cards/${detail.card.id}`}>카드 시세 상세 <ChevronRight/></a>
        <section className="auction-detail-history"><h2>최근 입찰 내역</h2>
          {recentBids.length===0
            ?<p>아직 입찰 내역이 없습니다.</p>
            :recentBids.map(bid=><div key={bid.id}><span><b>{bid.is_highest?'최고 입찰':'입찰 완료'}</b><small>{bid.bidder_alias} · {formatBidTime(bid.created_at)}</small></span><strong>{bid.amount.toLocaleString()}원</strong></div>)}
        </section>
      </section>
    </div>
    <section className="auction-seller-post">
      <div className="auction-seller-heading"><div className="auction-seller-avatar">{detail.seller.nickname.slice(0,2).toUpperCase()}</div><span><small>경매 등록자</small><b>{detail.seller.nickname}</b><em>거래 {detail.seller.trade_count}회 · 신뢰도 {detail.seller.trust_score}%</em></span></div>
      <div className="auction-seller-content"><small>SELLER NOTE</small><h2>경매 상품 설명</h2><p>{detail.description}</p>
        <dl>
          <div><dt>즉시 구매가</dt><dd>{detail.buy_now_price.toLocaleString()}원</dd></div>
          <div><dt>배송비</dt><dd>{detail.shipping_fee.toLocaleString()}원</dd></div>
          {detail.seller_memo&&<div><dt>판매자 메모</dt><dd>{detail.seller_memo}</dd></div>}
        </dl>
        <div className="auction-seller-notice"><Info/><span><b>입찰 전 확인해 주세요.</b><small>등록 사진과 설명을 충분히 확인한 뒤 입찰해 주세요.</small></span></div>
      </div>
    </section>
  </main>{bidOpen&&<AuctionBidDialog auction={auction} onClose={()=>setBidOpen(false)}/>}</div>;
}
