import mockupData from '../mocks/mockup-data.json';
import {mapAuction,mapCard} from './auctionMapper';
import type {AuctionListRequestDto,AuctionResponseDto,CardListRequestDto,CardResponseDto,MockAuctionResponseDto} from '../dto/auctionDto';

const cards=(mockupData.cards as CardResponseDto[]).map(mapCard);
const cardResponses=mockupData.cards as CardResponseDto[];
const auctions=(mockupData.auctions as MockAuctionResponseDto[]).map(auction=>{
  const card=cardResponses.find(item=>item.id===auction.card_id);
  if(!card)throw new Error(`Mock card ${auction.card_id} not found`);
  const response:AuctionResponseDto={
    ...auction,
    seller:{id:1,nickname:'mock-seller',trade_count:0,trust_score:0},
    minimum_bid:auction.current_price+auction.bid_increment,
    starts_at:new Date(new Date(auction.ends_at).getTime()-12*60*60*1000).toISOString(),
    version:1,
    card:{
      id:card.id,
      name:card.name,
      set_name:'Pokemon Trading Card Game',
      psa_grade:card.psa_grade,
      language:card.language,
      thumbnail_url:card.thumbnail_url??null,
    },
  };
  return {...mapAuction(response),card:mapCard(card)};
});

export async function fetchMockCards(query:CardListRequestDto){
  const result=cards.filter(card=>card.name.includes(query.keyword)&&(query.psaGrade===null||card.psaGrade===query.psaGrade));
  return [...result].sort((a,b)=>query.sort==='FAVORITE'
    ?b.bidCount-a.bidCount
    :query.sort==='REGISTERED'
      ?a.id-b.id
      :b.marketPrice-a.marketPrice);
}

export async function fetchMockAuctions(query:AuctionListRequestDto){
  const result=auctions.filter(item=>item.card.name.includes(query.keyword)&&(query.psaGrade===null||item.card.psaGrade===query.psaGrade));
  return [...result].sort((a,b)=>query.sort==='LATEST'?b.id-a.id:query.sort==='PRICE_HIGH'?b.currentPrice-a.currentPrice:query.sort==='PRICE_LOW'?a.currentPrice-b.currentPrice:query.sort==='CHANGE_HIGH'?b.card.changeRate-a.card.changeRate:b.bidCount-a.bidCount);
}
