import {useQuery} from '@tanstack/react-query';
import {Bookmark,Share2} from 'lucide-react';
import {Header} from '../../components';
import {cardQueries} from '../../queries/auctionQueries';
import {useWishlist} from '../../hooks/useWishlist';
import PriceChangeAreaChart from './PriceChangeAreaChart';

const money=(value:number)=>`${value.toLocaleString()}원`;

export default function CardPriceDetailPage(){
  const cardId=Number(window.location.pathname.split('/').filter(Boolean).pop());
  const{data:card,isPending,error}=useQuery(cardQueries.detail(cardId));
  const{favoriteCardIds,toggleFavorite,isPending:wishlistPending}=useWishlist();
  if(isPending)return <div className="detail-page price-detail-page"><Header/><main><p>카드 시세를 불러오는 중…</p></main></div>;
  if(error||!card)return <div className="detail-page price-detail-page"><Header/><main><p className="form-error">카드 시세를 불러오지 못했습니다.</p></main></div>;

  const image=card.image_url||'/assets/pikachu-promo-card.png';
  const saved=favoriteCardIds.includes(card.id);
  const priceRange=`${money(card.low_price)} - ${money(card.high_price)}`;
  return <div className="detail-page price-detail-page">
    <Header/>
    <div className="detail-layout">
      <section className="product-visual">
        <img className="product-image" src={image} alt={card.name}/>
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
          <button className="icon-action" aria-label="공유" onClick={()=>navigator.clipboard?.writeText(window.location.href)}><Share2/><small>공유</small></button>
          <a className="buy detail-bid-button" href={`/auction?keyword=${encodeURIComponent(card.name)}`}>진행 경매 보기 ({card.active_auction_count}개)</a>
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
