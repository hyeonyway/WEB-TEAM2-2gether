import {useMemo} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Bookmark,Share2} from 'lucide-react';
import {Header} from '../../components';
import {cardQueries} from '../../queries/auctionQueries';
import {useCardFavorites} from '../../hooks/useCardFavorites';

const money=(value:number)=>`${value.toLocaleString()}원`;

export default function CardPriceDetailPage(){
  const cardId=Number(window.location.pathname.split('/').filter(Boolean).pop());
  const{data:card,isPending,error}=useQuery(cardQueries.detail(cardId));
  const{favoriteCardIds,toggleFavorite}=useCardFavorites();
  const chartPath=useMemo(()=>{
    if(!card?.history.length)return '';
    const prices=card.history.map(point=>point.average_price);
    const min=Math.min(...prices),max=Math.max(...prices),range=Math.max(1,max-min);
    return card.history.map((point,index)=>{
      const x=card.history.length===1?325:index*650/(card.history.length-1);
      const y=205-(point.average_price-min)/range*185;
      return `${index?'L':'M'}${x.toFixed(1)} ${y.toFixed(1)}`;
    }).join(' ');
  },[card]);

  if(isPending)return <div className="detail-page price-detail-page"><Header/><main><p>카드 시세를 불러오는 중…</p></main></div>;
  if(error||!card)return <div className="detail-page price-detail-page"><Header/><main><p className="form-error">카드 시세를 불러오지 못했습니다.</p></main></div>;

  const image=card.image_url||'/assets/pikachu-promo-card.png';
  const saved=favoriteCardIds.includes(card.id);
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
          <h1>{money(card.low_price)} - {money(card.high_price)}</h1>
          <p>{card.name}</p>
          <small>{card.set_name}{card.card_number&&` · ${card.card_number}`}</small>
          <u>포켓몬 · 트레이딩 카드 · PSA {card.psa_grade??'-'}</u>
        </div>
        <div className="buy-row price-buy-row">
          <button className={'icon-action '+(saved?'saved':'')} onClick={()=>toggleFavorite(card.id)} aria-label="관심 카드"><Bookmark/><small>{card.favorite_count}</small></button>
          <button className="icon-action" aria-label="공유" onClick={()=>navigator.clipboard?.writeText(window.location.href)}><Share2/><small>공유</small></button>
          <a className="buy detail-bid-button" href={`/auction?keyword=${encodeURIComponent(card.name)}`}>진행 경매 보기 ({card.active_auction_count}개)</a>
        </div>
        <section className="detail-price-summary">
          <div><span>최근 시세</span><strong>{money(card.market_price)}</strong><em>{card.change_rate>=0?'+':''}{card.change_rate.toFixed(1)}%</em></div>
          <div><span>과거 입찰 건수</span><strong>{card.bid_count}건</strong></div>
          <div><span>현재 경매 수</span><strong>{card.active_auction_count}개</strong></div>
        </section>
        <section className="price-trend">
          <div className="price-trend-head"><div><h2>시세 변화 추이</h2><p>최근 30일 카드 평균 가격과 거래량입니다.</p></div><span>30일</span></div>
          {card.history.length?<div className="detail-chart">
            <div className="detail-bars">{card.history.map(point=><i key={point.date} style={{height:Math.min(90,20+point.trade_count*3)}}/>)}</div>
            <svg viewBox="0 0 650 220" preserveAspectRatio="none"><path className="detail-line" d={chartPath}/></svg>
            <div className="detail-dates">{card.history.filter((_,index)=>index%Math.max(1,Math.floor(card.history.length/6))===0).map(point=><span key={point.date}>{point.date.slice(5).replace('-','/')}</span>)}</div>
          </div>:<p className="catalog-count">최근 30일 시세 데이터가 없습니다.</p>}
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
