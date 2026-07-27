import {request} from './httpClient';
import {fetchMockAuctions,fetchMockCards} from './mockAuctionApi';
import {mapAuction,mapCard,resolveImageUrl} from './auctionMapper';
import type {AuctionDto,AuctionListRequestDto,AuctionResponseDto,CardDetailResponseDto,CardDto,CardListRequestDto,CardResponseDto,PageResponseDto} from '../dto/auctionDto';

const useMock=import.meta.env.VITE_USE_MOCK_API==='true';
const params=(query:CardListRequestDto)=>new URLSearchParams({keyword:query.keyword,...(query.psaGrade===null?{}:{psaGrade:String(query.psaGrade)})});

export async function fetchCards(query:CardListRequestDto):Promise<CardDto[]>{
  if(useMock)return fetchMockCards(query);
  const response=await request<PageResponseDto<CardResponseDto>>(`/api/cards?${params(query)}`);
  return response.content.map(mapCard);
}

export async function fetchCardPage(
  query:CardListRequestDto,
  page:number,
  size=20,
):Promise<PageResponseDto<CardDto>>{
  if(useMock){
    const cards=await fetchMockCards(query);
    const start=page*size;
    return {
      content:cards.slice(start,start+size),
      page,
      size,
      total_elements:cards.length,
      has_next:start+size<cards.length,
    };
  }
  const search=params(query);
  search.set('page',String(page));
  search.set('size',String(size));
  const response=await request<PageResponseDto<CardResponseDto>>(`/api/cards?${search}`);
  return {...response,content:response.content.map(mapCard)};
}

export async function fetchCardDetail(cardId:number):Promise<CardDetailResponseDto>{
  if(useMock){
    const card=(await fetchMockCards({keyword:'',psaGrade:null})).find(item=>item.id===cardId);
    if(!card)throw new Error('카드를 찾을 수 없습니다.');
    return {
      id:card.id,name:card.name,set_name:'Pokemon Trading Card Game',card_number:null,rarity:null,
      market_price:card.marketPrice,low_price:Math.round(card.marketPrice*.9/1000)*1000,
      high_price:Math.round(card.marketPrice*1.08/1000)*1000,average_price:card.marketPrice,
      change_rate:card.changeRate,weekly_change_rate:card.changeRate*1.8,
      monthly_change_rate:card.changeRate*3.4,trade_count:card.bidCount,bid_count:card.bidCount,
      active_auction_count:1,favorite_count:0,psa_grade:card.psaGrade,language:card.language,
      image_url:'/assets/pikachu-promo-card.png',history:[],
    };
  }
  const response=await request<CardDetailResponseDto>(`/api/cards/${cardId}`);
  return {...response,image_url:resolveImageUrl(response.image_url)};
}

export async function fetchAuctions(query:AuctionListRequestDto):Promise<AuctionDto[]>{
  if(useMock)return fetchMockAuctions(query);
  const search=params(query);search.set('sort',query.sort);
  const response=await request<PageResponseDto<AuctionResponseDto>>(`/api/auctions?${search}`);
  return response.content.map(mapAuction);
}
