import {request} from './httpClient';
import {fetchMockAuctions,fetchMockCards} from './mockAuctionApi';
import {mapAuction,mapCard,resolveImageUrl} from './auctionMapper';
import {isMockApiEnabled} from './mockApiConfig';
import type {AuctionDto,AuctionListRequestDto,AuctionResponseDto,CardDetailResponseDto,CardDto,CardListRequestDto,CardResponseDto,PageResponseDto} from '../dto/auctionDto';

const params=(query:{keyword:string;psaGrade:string|null;sort?:string})=>new URLSearchParams({
  keyword:query.keyword,
  ...(query.psaGrade===null?{}:{psaGrade:String(query.psaGrade)}),
  ...(query.sort?{sort:query.sort}:{}),
});

const mockPriceHistory=(marketPrice:number,monthlyChangeRate:number)=>{
  const today=new Date();
  const startPrice=Math.max(1,Math.round(marketPrice/(1+monthlyChangeRate/100)));
  return Array.from({length:30},(_,index)=>{
    const date=new Date(today);
    date.setDate(today.getDate()-(30-index));
    const progress=index/29;
    const wave=Math.sin(index*.75)*marketPrice*.018;
    const averagePrice=Math.max(1,Math.round(startPrice+(marketPrice-startPrice)*progress+wave));
    const traded=index%8!==3;
    return {
      date:date.toISOString(),
      average_price:traded?averagePrice:null,
      ended_auction_count:traded?Math.max(1,Math.round(
        3+Math.sin(index*.68)*2+Math.cos(index*.31),
      )):0,
      change_rate:index===0?0:Number(((averagePrice-startPrice)/startPrice*100).toFixed(2)),
      weekly_change_rate:0,
      monthly_change_rate:Number(((averagePrice-startPrice)/startPrice*100).toFixed(2)),
    };
  });
};

export async function fetchCards(query:CardListRequestDto):Promise<CardDto[]>{
  if(isMockApiEnabled())return fetchMockCards(query);
  const response=await request<PageResponseDto<CardResponseDto>>(`/api/cards?${params(query)}`);
  return response.content.map(mapCard);
}

export async function fetchCardPage(
  query:CardListRequestDto,
  page:number,
  size=20,
):Promise<PageResponseDto<CardDto>>{
  if(isMockApiEnabled()){
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
  if(isMockApiEnabled()){
    const card=(await fetchMockCards({keyword:'',psaGrade:null})).find(item=>item.id===cardId);
    if(!card)throw new Error('카드를 찾을 수 없습니다.');
    const history=mockPriceHistory(card.marketPrice,card.changeRate*3.4);
    const tradedPrices=history.flatMap(point=>point.average_price===null?[]:[point.average_price]);
    const averagePrice=tradedPrices.length
      ?Math.round(tradedPrices.reduce((sum,price)=>sum+price,0)/tradedPrices.length)
      :0;
    return {
      id:card.id,name:card.name,set_name:'Pokemon Trading Card Game',rarity:null,
      market_price:card.marketPrice,low_price:card.lowPrice,
      high_price:card.highPrice,average_price:averagePrice,
      change_rate:card.changeRate,weekly_change_rate:card.changeRate*1.8,
      monthly_change_rate:card.changeRate*3.4,bid_count:card.bidCount,
      ended_auction_count:Math.max(1,Math.round(card.bidCount/4)),
      active_auction_count:1,wishlist_count:0,psa_grade:card.psaGrade,language:card.language,
      image_url:'/assets/pikachu-promo-card.png',
      history,
    };
  }
  const response=await request<CardDetailResponseDto>(`/api/cards/${cardId}`);
  return {...response,image_url:resolveImageUrl(response.image_url)};
}

export async function fetchAuctions(query:AuctionListRequestDto):Promise<AuctionDto[]>{
  if(isMockApiEnabled())return fetchMockAuctions(query);
  const search=params(query);search.set('sort',query.sort);
  const response=await request<PageResponseDto<AuctionResponseDto>>(`/api/auctions?${search}`);
  return response.content.map(mapAuction);
}
