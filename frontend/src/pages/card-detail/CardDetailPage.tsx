import {useQuery} from '@tanstack/react-query';
import {Bookmark,Share2} from 'lucide-react';
import {Link,useParams} from 'react-router-dom';
import {Header,showToast} from '../../components';
import {cardQueries} from '../../queries/auctionQueries';
import {useWishlist} from '../../hooks/useWishlist';
import PriceChangeAreaChart from './PriceChangeAreaChart';

const money=(value:number)=>`${value.toLocaleString()}원`;

export default function CardPriceDetailPage(){
  const params=useParams();
  const cardId=Number(params.cardId);
  const{data:card,isPending,error}=useQuery(cardQueries.detail(cardId));
  const{isFavorite,toggleFavorite,isPending:wishlistPending}=useWishlist();
  if(isPending)return <CardPriceDetailSkeleton/>;
  if(error||!card)return <div className="detail-page price-detail-page"><Header/><main><p className="form-error">카드 시세를 불러오지 못했습니다.</p></main></div>;

  const image=card.image_url||'/assets/pikachu-promo-card.png';
  const saved=isFavorite(card.id);
  const priceRange=`${money(card.low_price)} - ${money(card.high_price)}`;
  const copyCurrentLink=async()=>{
    try{
      if(!navigator.clipboard)throw new Error('Clipboard API is unavailable');
      await navigator.clipboard.writeText(window.location.href);
      showToast('시세 상세 링크를 복사했습니다.');
    }catch{
      showToast('링크를 복사하지 못했습니다.');
    }
  };
  return <div className="detail-page price-detail-page">
    <Header/>
    <div className="detail-layout">
      <section className="product-visual">
        <div className="product-image-viewport"><img className="product-image" src={image} alt={card.name}/></div>
        <div className="thumbs"><img src={image} alt="선택된 카드 이미지"/></div>
      </section>
      <section className="product-info">
        <div className="title-block">
          <div className="detail-grades"><span className="grade">PSA {card.psa_grade??'-'}</span><span className="grade">{card.language}</span></div>
          <h1>{priceRange}</h1>
          <p>{card.name}</p>
          <small>{card.set_name}</small>
          <u>포켓몬 · 트레이딩 카드 · PSA {card.psa_grade??'-'}</u>
        </div>
        <div className="buy-row price-buy-row">
          <button className={'icon-action '+(saved?'saved':'')} disabled={wishlistPending} onClick={()=>toggleFavorite(card.id)} aria-label="관심 카드"><Bookmark/><small>{card.wishlist_count}</small></button>
          <button type="button" className="icon-action" aria-label="공유" onClick={()=>void copyCurrentLink()}><Share2/><small>공유</small></button>
          <Link className="buy detail-bid-button" to={`/auction?keyword=${encodeURIComponent(card.name)}`}>진행 경매 보기 ({card.active_auction_count}개)</Link>
        </div>
        <section className="detail-price-summary">
          <div className="detail-price-range"><span>최근 시세 범위</span><strong>{priceRange}</strong><em>{card.change_rate>=0?'+':''}{card.change_rate.toFixed(1)}%</em></div>
          <div><span>최근 30일 경매 건수</span><strong>{card.ended_auction_count.toLocaleString()}건</strong></div>
          <div><span>최근 30일 입찰 건수</span><strong>{card.bid_count.toLocaleString()}건</strong></div>
        </section>
        <section className="price-trend">
          <div className="price-trend-head"><div><h2>시세 변화 추이</h2><p>일자별 평균 낙찰가와 총 낙찰 수입니다.</p></div><span>30일</span></div>
          <PriceChangeAreaChart history={card.history}/>
          <div className="trend-stats">
            <span>1일 변화<b>{card.change_rate>=0?'+':''}{card.change_rate.toFixed(1)}%</b></span>
            <span>7일 변화<b>{card.weekly_change_rate>=0?'+':''}{card.weekly_change_rate.toFixed(1)}%</b></span>
            <span>30일 변화<b>{card.monthly_change_rate>=0?'+':''}{card.monthly_change_rate.toFixed(1)}%</b></span>
            <span>평균 거래가<strong>{money(card.average_price)}</strong></span>
          </div>
        </section>
      </section>
    </div>
  </div>;
}

function CardPriceDetailSkeleton(){
  return <div className="detail-page price-detail-page price-detail-skeleton">
    <Header/>
    <div className="detail-layout" role="status" aria-label="카드 시세 상세를 불러오는 중" aria-busy="true">
      <section className="product-visual">
        <div className="detail-skeleton-image skeleton-pulse"/>
        <div className="detail-skeleton-thumbnail skeleton-pulse"/>
      </section>
      <section className="product-info">
        <div className="detail-skeleton-grades">
          <i className="skeleton-pulse"/><i className="skeleton-pulse"/>
        </div>
        <div className="detail-skeleton-title">
          <i className="skeleton-pulse"/><i className="skeleton-pulse"/>
          <i className="skeleton-pulse"/><i className="skeleton-pulse"/>
        </div>
        <div className="detail-skeleton-actions">
          <i className="skeleton-pulse"/><i className="skeleton-pulse"/>
          <i className="skeleton-pulse"/>
        </div>
        <section className="detail-skeleton-summary">
          {Array.from({length:3},(_,index)=><div key={index}>
            <i className="skeleton-pulse"/><b className="skeleton-pulse"/>
          </div>)}
        </section>
        <section className="detail-skeleton-trend">
          <div><i className="skeleton-pulse"/><b className="skeleton-pulse"/></div>
          <div className="detail-skeleton-chart">
            {Array.from({length:12},(_,index)=><i
              className="skeleton-pulse"
              style={{height:`${20+(index*23)%65}%`}}
              key={index}
            />)}
          </div>
          <div className="detail-skeleton-stats">
            {Array.from({length:4},(_,index)=><i className="skeleton-pulse" key={index}/>)}
          </div>
        </section>
      </section>
    </div>
  </div>;
}
