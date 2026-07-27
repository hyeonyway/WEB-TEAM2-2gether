import {Search} from 'lucide-react';
import type {CardDto} from '../../../dto/auctionDto';
import CardArtwork from './CardArtwork';
import CardFavoriteButton from './CardFavoriteButton';

export default function CardCatalog({cards}:{cards:CardDto[]}){
  if(!cards.length)return <div className="filter-empty"><Search/><b>조건에 맞는 카드가 없습니다.</b></div>;
  return <section className="catalog-grid">{cards.map(card=>{
    const low=Math.round(card.marketPrice*.9/1000)*1000;
    const high=Math.round(card.marketPrice*1.08/1000)*1000;
    return <a className="catalog-card" href={`/cards/${card.id}`} key={card.id}>
      <CardFavoriteButton cardId={card.id}/>
      <div className="catalog-art"><CardArtwork theme={card.theme} imageUrl={card.imageUrl} name={card.name}/><span>PSA {card.psaGrade}</span></div>
      <div className="catalog-card-body">
        <small>Pokemon Trading Card Game</small><h2>{card.name}</h2><p>PSA {card.psaGrade} · {card.language}</p>
        <div className="catalog-preview">
          <span className="market-range">시세<strong>{low.toLocaleString()}원 - {high.toLocaleString()}원</strong></span>
          <span>과거 입찰 건수<strong>{card.bidCount}건</strong></span>
        </div>
      </div>
    </a>;
  })}</section>;
}
