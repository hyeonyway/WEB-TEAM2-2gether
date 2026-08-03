import {Search} from 'lucide-react';
import {normalizePsaGrade} from '../../../api/auctionMapper';
import type {CardDto} from '../../../dto/auctionDto';
import CardArtwork from './CardArtwork';
import CardFavoriteButton from './CardFavoriteButton';

const priceRange=(low:number,high:number)=>
  `${low.toLocaleString()}원 - ${high.toLocaleString()}원`;

export default function CardCatalog({cards}:{cards:CardDto[]}){
  if(!cards.length)return <div className="filter-empty"><Search/><b>조건에 맞는 카드가 없습니다.</b></div>;
  return <section className="catalog-grid">{cards.map(card=>{
    const psaGrade=normalizePsaGrade(card.psaGrade);
    return <a className="catalog-card" href={`/cards/${card.id}`} key={card.id}>
      <CardFavoriteButton cardId={card.id}/>
      <div className="catalog-art"><CardArtwork theme={card.theme} imageUrl={card.imageUrl} name={card.name}/><span>PSA {psaGrade}</span></div>
      <div className="catalog-card-body">
        <small>Pokemon Trading Card Game</small><h2>{card.name}</h2><p>PSA {psaGrade} · {card.language}</p>
        <div className="catalog-preview">
          <span className="market-range">시세<strong>{priceRange(card.lowPrice,card.highPrice)}</strong></span>
          <span>최근 30일 입찰 건수<strong>{card.bidCount.toLocaleString()}건</strong></span>
        </div>
      </div>
    </a>;
  })}</section>;
}
